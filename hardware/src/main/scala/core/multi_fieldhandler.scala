package vta.core

import Chisel._
import chisel3.{Printable, VecInit}
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._


class FieldDispatchRouter(numHandlers: Int)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val fields_req_in = Flipped(Decoupled(new DescrToHandlerBundle))
    val to_fieldhandlers = Vec(numHandlers, Decoupled(new DescrToHandlerBundle))
  })


  val index = RegInit(0.U(log2Up(numHandlers+1).W))

  val outputQueues = VecInit(Seq.fill(numHandlers){Module(new Queue(new DescrToHandlerBundle, 4)).io})

  for (i <- 0 until numHandlers) {
    io.to_fieldhandlers(i) <> outputQueues(i).deq

    outputQueues(i).enq.bits <> io.fields_req_in.bits
    outputQueues(i).enq.valid := io.fields_req_in.valid && (index === i.U)
  }
  io.fields_req_in.ready := outputQueues(index).enq.ready

  when (io.fields_req_in.fire) {
    when (index === (numHandlers-1).U) {
      index := 0.U
    } .otherwise {
      index := index + 1.U
    }
  }

}


class MemWriteArbiter(numHandlers: Int)(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val from_fieldhandlers = Vec(numHandlers, Flipped(Decoupled(new WriterBundle)))
    val write_reqs_out = Decoupled(new WriterBundle)
  })


  val index = RegInit(0.U(log2Up(numHandlers+1).W))

  for (i <- 0 until numHandlers) {
    io.from_fieldhandlers(i).ready := (index === i.U) && io.write_reqs_out.ready
  }

  io.write_reqs_out.valid := io.from_fieldhandlers(index).valid
  io.write_reqs_out.bits := io.from_fieldhandlers(index).bits

  when (io.write_reqs_out.fire && io.write_reqs_out.bits.last_for_arbitration_round) {
    when (index === (numHandlers-1).U) {
      index := 0.U
    } .otherwise {
      index := index + 1.U
    }
  }

}
