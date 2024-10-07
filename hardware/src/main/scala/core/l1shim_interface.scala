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

  // Define request and response queues as DecoupledIO interfaces
  val reqQueue = Wire(Flipped(Decoupled(new L1ReqInternal)))
  val outstandingReqQueue = Module(new Queue(new L1ReqInternal, 16)).io // Queue for outstanding requests
  val respQueue = Wire(Decoupled(new L1RespInternal))

  // Handle request queueing
  if (queueRequests) {
    val queue = Module(new Queue(new L1ReqInternal, 4))
    queue.io.enq <> io.userif.req
    reqQueue <> queue.io.deq
  } else {
    reqQueue <> io.userif.req
  }

  // Handle response queueing
  if (queueResponses) {
    val queue = Module(new Queue(new L1RespInternal, 4))
    queue.io.enq <> respQueue
    io.userif.resp <> queue.io.deq
  } else {
    respQueue <> io.userif.resp
  }

  // Outstanding read operations counter
  val outstandingReads = RegInit(0.U(log2Ceil(4 + 1).W))
  io.userif.no_memops_inflight := (outstandingReads === 0.U)

  // Define two queues to store 64-bit vme_rd data, wrapped in a Vec
  val vmeDataQueues = VecInit(Seq.fill(2)(Module(new Queue(UInt(64.W), 4)).io))
  
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

  // Handle read requests
  io.vme_rd.cmd.valid := reqQueue.valid
  io.vme_rd.cmd.bits.addr := reqQueue.bits.addr
  io.vme_rd.cmd.bits.len := calcAxiLen(reqQueue.bits.size)
  io.vme_rd.cmd.bits.tag := clientTag
  reqQueue.ready := io.vme_rd.cmd.ready

  when(reqQueue.fire) {
    ProtoaccLogger.logInfo("[L1ReadMem] reqQueue fire addr 0x%x\n", reqQueue.bits.addr)
  }

  // ready is ignored
  outstandingReqQueue.enq.valid := reqQueue.fire
  outstandingReqQueue.enq.bits := reqQueue.bits

  when(io.vme_rd.cmd.fire) {
    outstandingReads := outstandingReads + 1.U
    ProtoaccLogger.logInfo("[L1ReadMem] fire read cmd\n")
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
    // Switch between queues for the next data beat
    when(outstandingReqQueue.deq.bits.size === 4.U) {
      ProtoaccLogger.logInfo("[L1ReadMem] reading 128-bit data at address  0x%x", outstandingReqQueue.deq.bits.addr)
      popQueue := popQueue
    }.elsewhen(outstandingReqQueue.deq.bits.size <= 3.U){
      ProtoaccLogger.logInfo("[L1ReadMem] reading <= 128-bit data at address  0x%x", outstandingReqQueue.deq.bits.addr)

      popQueue := ~popQueue
    }
  }

  // Handle completion of read responses
  when (io.vme_rd.data.valid && io.vme_rd.data.bits.last) {
    outstandingReads := outstandingReads - 1.U
  }
}


class L1WriteMemHelper(
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
