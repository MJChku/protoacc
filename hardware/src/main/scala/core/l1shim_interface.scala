package vta.core

import Chisel._
import chisel3.{Printable}
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._
import vta.shell._


class L1ReqInternal extends Bundle {
  val addr = UInt(64.W)
  val size = UInt(3.W)
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
    printInfo: String = "",
    queueRequests: Boolean = true,
    queueResponses: Boolean = true
)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val userif = Flipped(new L1MemHelperBundle)
    val vme_rd = new VMEReadMaster
  })
  // Define reqQueue and respQueue as consistent DecoupledIO interfaces
  val reqQueue = Wire(Flipped(Decoupled(new L1ReqInternal)))
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

  // Default signals
  io.vme_rd.cmd.valid := false.B
  io.vme_rd.cmd.bits := 0.U.asTypeOf(new VMECmd)
  io.vme_rd.data.ready := false.B

  reqQueue.ready := false.B
  respQueue.valid := false.B
  respQueue.bits := 0.U.asTypeOf(new L1RespInternal)

  // Handle read requests
  when(reqQueue.valid && reqQueue.bits.cmd === 0.U) {
    io.vme_rd.cmd.valid := true.B
    io.vme_rd.cmd.bits.addr := reqQueue.bits.addr
    io.vme_rd.cmd.bits.len := reqQueue.bits.size
    io.vme_rd.cmd.bits.tag := 0.U // Tag can be used if needed

    when(io.vme_rd.cmd.ready) {
      reqQueue.ready := true.B
      outstandingReads := outstandingReads + 1.U
    }
  }

  // Handle read responses
  io.vme_rd.data.ready := respQueue.ready
  when(io.vme_rd.data.valid && io.vme_rd.data.ready) {
    respQueue.valid := true.B
    respQueue.bits.data := io.vme_rd.data.bits.data
    when(io.vme_rd.data.bits.last) {
      outstandingReads := outstandingReads - 1.U
    }
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

  // Default signals
  io.vme_wr.cmd.valid := false.B
  io.vme_wr.cmd.bits := 0.U.asTypeOf(new VMECmd)
  io.vme_wr.data.valid := false.B
  io.vme_wr.data.bits := 0.U.asTypeOf(new VMEWriteData)
  // Note: io.vme_wr.ack is an Input, so we don't assign to it.

  reqQueue.ready := false.B

  // Handle write requests
  when(reqQueue.valid && reqQueue.bits.cmd === 1.U) {
    io.vme_wr.cmd.valid := true.B
    io.vme_wr.cmd.bits.addr := reqQueue.bits.addr
    io.vme_wr.cmd.bits.len := reqQueue.bits.size
    io.vme_wr.cmd.bits.tag := 0.U // Tag can be used if needed

    when(io.vme_wr.cmd.ready) {
      io.vme_wr.data.valid := true.B
      io.vme_wr.data.bits.data := reqQueue.bits.data
      io.vme_wr.data.bits.strb := Fill(io.vme_wr.data.bits.strb.getWidth, 1.U) // All bytes valid
      when(io.vme_wr.data.ready) {
        reqQueue.ready := true.B
        outstandingWrites := outstandingWrites + 1.U
      }
    }
  }

  // Handle write acknowledgment
  when(io.vme_wr.ack) {
    outstandingWrites := outstandingWrites - 1.U
  }
}
