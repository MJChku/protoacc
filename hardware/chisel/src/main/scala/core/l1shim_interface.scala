package vta.core

import Chisel._
import chisel3.{Printable, VecInit}
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._
import vta.shell._


class L1ReqInternal extends Bundle {
  val addr = UInt(64.W)
  val size = UInt(4.W)
  val data = UInt(128.W)
  val cmd  = UInt(4.W) // 0 for read, 1 for write
}

class L1RespInternal extends Bundle {
  val data = UInt(128.W)
}

class L1MemHelperBundle extends Bundle {
  val req                = Decoupled(new L1ReqInternal)
  val resp               = Flipped(Decoupled(new L1RespInternal))
  val no_memops_inflight = Input(Bool())
}

class MasterReq(numMasters: Int) extends Bundle {
  val req = new L1ReqInternal
  val masterID = UInt(log2Ceil(numMasters).W)
}


class L1ReadMemHelper(
    clientTag : UInt = 0.U,
    printInfo: String = "",
    queueRequests: Boolean = true,
    queueResponses: Boolean = true
)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val userif = Flipped(new L1MemHelperBundle)
    val vme_rd = new VMEReadMaster
  })
  // NOTE: VME doesnt stop on not ready for read response!

  // Define request and response queues as DecoupledIO interfaces
  val reqQueue = Wire(Flipped(Decoupled(new L1ReqInternal)))
  val outstandingReqQueue = Module(new Queue(new L1ReqInternal, 160)).io // Queue for outstanding requests
  val respQueue = Wire(Decoupled(new L1RespInternal))

  // Handle request queueing
  if (queueRequests) {
    val queue = Module(new Queue(new L1ReqInternal, 160))
    queue.io.enq <> io.userif.req
    reqQueue <> queue.io.deq
  } else {
    reqQueue <> io.userif.req
  }

  // Handle response queueing
  if (queueResponses) {
    val queue = Module(new Queue(new L1RespInternal, 160))
    queue.io.enq <> respQueue
    io.userif.resp <> queue.io.deq
  } else {
    respQueue <> io.userif.resp
  }

  // Outstanding read operations counter
  val outstandingReads = RegInit(0.U(64.W))
  val returnedReads = RegInit(0.U(64.W))
  io.userif.no_memops_inflight := (outstandingReads === returnedReads)

  // Define two queues to store 64-bit vme_rd data, wrapped in a Vec
  val vmeDataQueues = VecInit(Seq.fill(2)(Module(new Queue(UInt(64.W), 16)).io))
  
  // Register to track which queue to push to and which to pop from
  val activeQueue = RegInit(0.U(1.W)) // 0 or 1, indicating which queue to push to or pop from
  val popQueue = RegInit(0.U(1.W)) // 0 or 1, indicating which queue to pop from

  // Default signals
  io.vme_rd.cmd.valid := false.B
  io.vme_rd.cmd.bits := 0.U.asTypeOf(new VMECmd)
  io.vme_rd.data.ready := false.B

  respQueue.bits := 0.U.asTypeOf(new L1RespInternal)

  // Helper function to calculate AXI len based on L1 request size
  def calcAxiLen(size: UInt): UInt = {
    Mux(size === 4.U, 1.U, 0.U) // If size = 4 (16 bytes), len = 1 (2 beats), else len = 0 (1 beat)
  }

  val issue_mem_request_address = DecoupledHelper(
    reqQueue.valid,
    (outstandingReads - returnedReads) < 16.U,
    io.vme_rd.cmd.ready
  )
  // Handle read requests
  io.vme_rd.cmd.valid := issue_mem_request_address.fire(io.vme_rd.cmd.ready)
  reqQueue.ready := issue_mem_request_address.fire(reqQueue.valid)

  io.vme_rd.cmd.bits.addr := reqQueue.bits.addr
  io.vme_rd.cmd.bits.len := calcAxiLen(reqQueue.bits.size)
  // the tag is to identify the requests not the client 
  io.vme_rd.cmd.bits.tag := clientTag
  io.vme_rd.cmd.bits.tag := 0.U
  // io.vme_rd.cmd.bits.tag := outstandingReads
  // because VME doesn't stop on data resp not ready, we have to control the inflight requests

  when(reqQueue.fire) {
    ProtoaccLogger.logInfo("[L1ReadMem] reqQueue fire addr 0x%x, size %d \n", reqQueue.bits.addr, reqQueue.bits.size)
  }

  // ready is ignored
  outstandingReqQueue.enq.valid := reqQueue.fire
  outstandingReqQueue.enq.bits := reqQueue.bits

  when(io.vme_rd.cmd.fire) {
    outstandingReads := outstandingReads + 1.U
    ProtoaccLogger.logInfo("[L1ReadMem] fire read cmd; outstandingreads %d\n", outstandingReads)
  }

  // Handle incoming data from vme_rd and enqueue into the active queue
  io.vme_rd.data.ready := vmeDataQueues(activeQueue).enq.ready
  vmeDataQueues(activeQueue).enq.valid := io.vme_rd.data.valid
  vmeDataQueues(activeQueue).enq.bits := io.vme_rd.data.bits.data
  vmeDataQueues(~activeQueue).enq.valid := false.B

  when(io.vme_rd.data.fire) {
    // Switch between queues for the next data beat
    activeQueue := ~activeQueue
  }

  // at handshake of respQueue, outstandingReqQueue will be popped, because the data is ready
  outstandingReqQueue.deq.ready := respQueue.ready && respQueue.valid

  vmeDataQueues(popQueue).deq.ready := Mux(outstandingReqQueue.deq.bits.size === 4.U, vmeDataQueues(~popQueue).deq.valid && respQueue.ready, respQueue.ready)
  vmeDataQueues(~popQueue).deq.ready := Mux(outstandingReqQueue.deq.bits.size === 4.U, respQueue.ready, false.B)

  respQueue.valid := Mux(outstandingReqQueue.deq.bits.size === 4.U, vmeDataQueues(0).deq.valid && vmeDataQueues(1).deq.valid, vmeDataQueues(popQueue).deq.valid)
  val firstBeat = vmeDataQueues(popQueue).deq.bits
  val secondBeat = vmeDataQueues(~popQueue).deq.bits
  // Combine the two 64-bit beats into 128-bit data
  val lowerData = Mux(outstandingReqQueue.deq.bits.size === 3.U, firstBeat, 
                      Mux(outstandingReqQueue.deq.bits.size === 2.U, firstBeat & 0x00000000FFFFFFFFL.U, 
                        Mux(outstandingReqQueue.deq.bits.size === 1.U, firstBeat & 0x000000000000FFFFL.U, firstBeat & 0x00000000000000FFL.U))) 

  respQueue.bits.data := Mux(outstandingReqQueue.deq.bits.size === 4.U, Cat(secondBeat, firstBeat), Cat(0.U(64.W), lowerData) ) 
  

  when(vmeDataQueues(0).enq.fire){
    ProtoaccLogger.logInfo("[L1ReadMem] vmeDataQueues(0) enq data 0x%x\n", io.vme_rd.data.bits.data)
  }
  when(vmeDataQueues(1).enq.fire){
    ProtoaccLogger.logInfo("[L1ReadMem] vmeDataQueues(1) enq data 0x%x\n", io.vme_rd.data.bits.data)
  }

  when(vmeDataQueues(0).deq.fire){
    ProtoaccLogger.logInfo("[L1ReadMem] vmeDataQueues(0) fire data 0x%x; req 0x%x\n", vmeDataQueues(0).deq.bits, outstandingReqQueue.deq.bits.addr)
  }
  when(vmeDataQueues(1).deq.fire){
    ProtoaccLogger.logInfo("[L1ReadMem] vmeDataQueues(1) fire data 0x%x; req 0x%x\n", vmeDataQueues(1).deq.bits, outstandingReqQueue.deq.bits.addr)
  }

  when(respQueue.fire) {
    // outstandingReads := outstandingReads - 1.U
    returnedReads := returnedReads + 1.U
    // Switch between queues for the next data beat
    when(outstandingReqQueue.deq.bits.size === 4.U) {
      ProtoaccLogger.logInfo("[L1ReadMem] reading 128-bit data at address  0x%x; returnedReads %d", outstandingReqQueue.deq.bits.addr, returnedReads)
      popQueue := popQueue
    }.elsewhen(outstandingReqQueue.deq.bits.size <= 3.U){
      ProtoaccLogger.logInfo("[L1ReadMem] reading <= 128-bit data at address  0x%x; returnedReads %d", outstandingReqQueue.deq.bits.addr, returnedReads)

      popQueue := ~popQueue
    }
  }

  // Handle completion of read responses
  // when (io.vme_rd.data.valid && io.vme_rd.data.bits.last){
  //   outstandingReads := outstandingReads - 1.U
  // }
}


class __L1WriteMemHelper(
    printInfo: String = "",
    queueRequests: Boolean = true
)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val userif = Flipped(new L1MemHelperBundle)
    val vme_wr = new VMEWriteMaster
  })

  // Define reqQueue as a consistent DecoupledIO interface
  val reqQueue = Wire(Flipped(Decoupled(new L1ReqInternal)))

  // Handle request queueing
  if (queueRequests) {
    val queue = Module(new Queue(new L1ReqInternal, 4))
    queue.io.enq <> io.userif.req
    reqQueue <> queue.io.deq
  } else {
    reqQueue <> io.userif.req
  }

  // No responses for writes
  io.userif.resp.valid := false.B
  io.userif.resp.bits := 0.U.asTypeOf(new L1RespInternal)

  // Outstanding write operations counter
  val outstandingWrites = RegInit(0.U(log2Ceil(4 + 1).W))
  io.userif.no_memops_inflight := (outstandingWrites === 0.U)

  reqQueue.ready := io.vme_wr.cmd.ready
  io.vme_wr.cmd.valid := reqQueue.valid
  io.vme_wr.data.valid := reqQueue.valid

  io.vme_wr.cmd.bits.addr := reqQueue.bits.addr
  io.vme_wr.data.bits.data := reqQueue.bits.data
  io.vme_wr.data.bits.strb := Fill(io.vme_wr.data.bits.strb.getWidth, 1.U) // All bytes valid

  // this is wrong, need to fix
  io.vme_wr.cmd.bits.len := 0.U // Always 1 beat for writes
  io.vme_wr.cmd.bits.tag := 0.U // Tag can be used if needed

  when(io.vme_wr.cmd.fire){
    ProtoaccLogger.logInfo("[L1WriteMem] writing 128-bit data at address 0x%x with value 0x%x", reqQueue.bits.addr, reqQueue.bits.data)
  }
}

class L1WriteMemHelper(
    printInfo: String = "",
    queueRequests: Boolean = true,
    queueResponses: Boolean = true
)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val userif = Flipped(new L1MemHelperBundle)
    val vme_wr = new VMEWriteMaster
  })

  // Define request and response queues
  val reqQueue = Wire(Flipped(Decoupled(new L1ReqInternal)))
  val outstandingReqQueue = Module(new Queue(new L1ReqInternal, 16)).io
  val respQueue = Wire(Decoupled(new L1RespInternal))
  
  // Handle request queueing
  if (queueRequests) {
    val queue = Module(new Queue(new L1ReqInternal, 16))
    queue.io.enq <> io.userif.req
    reqQueue <> queue.io.deq
  } else {
    reqQueue <> io.userif.req
  }

    // No responses for writes
  io.userif.resp.valid := false.B
  io.userif.resp.bits := 0.U.asTypeOf(new L1RespInternal)

  // Outstanding write operations counters
  val totalWrites = RegInit(0.U(64.W))
  val outstandingWrites = RegInit(0.U(64.W))
  val returnedWrites = RegInit(0.U(64.W))

  when(io.userif.req.fire){
    totalWrites := totalWrites + 1.U
  }

  // when(queue.io.enq.fire) {
  //     outstandingWrites := outstandingWrites + 1.U
  //   }

  io.userif.no_memops_inflight := (totalWrites === returnedWrites) 

  // Default signals
  io.vme_wr.cmd.valid := false.B
  io.vme_wr.cmd.bits := 0.U.asTypeOf(new VMECmd)
  io.vme_wr.data.valid := false.B
  io.vme_wr.data.bits := 0.U.asTypeOf(new VMEWriteData)

  // Helper function to calculate AXI len based on L1 request size
  def calcAxiLen(size: UInt): UInt = {
    Mux(size === 4.U, 1.U, 0.U) // If size = 4 (16 bytes), len = 1 (2 beats), else len = 0 (1 beat)
  }

  // Control logic to issue write commands
  val issue_mem_request = DecoupledHelper(
    reqQueue.valid,
    (outstandingWrites - returnedWrites) < 16.U,
    io.vme_wr.cmd.ready
  )

  // Issue write commands
  io.vme_wr.cmd.valid := issue_mem_request.fire(io.vme_wr.cmd.ready)
  reqQueue.ready := issue_mem_request.fire(reqQueue.valid)

  io.vme_wr.cmd.bits.addr := reqQueue.bits.addr
  io.vme_wr.cmd.bits.len := calcAxiLen(reqQueue.bits.size)
  io.vme_wr.cmd.bits.tag := 0.U

  outstandingReqQueue.enq.bits := reqQueue.bits
  outstandingReqQueue.enq.valid := reqQueue.fire

  when(io.vme_wr.cmd.fire) {
    outstandingWrites := outstandingWrites + 1.U
    ProtoaccLogger.logInfo("[L1WriteMem] fire write cmd; outstandingWrites %d\n", outstandingWrites)
  }

  // Control logic for sending write data
  val sendData = RegInit(false.B)
  val dataBeat = RegInit(0.U(1.W)) // 0 or 1, to track which beat we are sending
  val currentReq = outstandingReqQueue.deq.bits

  io.vme_wr.data.valid := true.B
  io.vme_wr.data.bits.data := Mux(dataBeat === 0.U, currentReq.data(63, 0), currentReq.data(127, 64))
  io.vme_wr.data.bits.strb := Fill(io.vme_wr.data.bits.strb.getWidth, 1.U) // All bytes valid 
  
  when(io.vme_wr.data.fire) {
    when(currentReq.size === 4.U && dataBeat === 0.U) {
      // For 128-bit writes, proceed to send the second beat
      dataBeat := 1.U
    }.otherwise {
      // For other sizes or after sending both beats
      dataBeat := 0.U
    }
  }

  outstandingReqQueue.deq.ready := io.vme_wr.ack
  // Handle write acknowledgments
  when(io.vme_wr.ack) {
    returnedWrites := returnedWrites + 1.U
    // ProtoaccLogger.logInfo2("[L1WriteMem] received write ack; returnedWrites %d; outstandingWrites %d\n", returnedWrites, outstandingWrites)
  }
}
