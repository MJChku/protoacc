package vta.core

import Chisel._
import chisel3.{Printable}
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._
import vta.util._

object DecoupledHelper {
  def apply(rvs: Bool*) = new DecoupledHelper(rvs)
}

class DecoupledHelper(val rvs: Seq[Bool]) {
  def fire(exclude: Bool, includes: Bool*) = {
    require(rvs.contains(exclude), "Excluded Bool not present in DecoupledHelper! Note that DecoupledHelper uses referential equality for exclusion! If you don't want to exclude anything, use fire()!")
    (rvs.filter(_ ne exclude) ++ includes).reduce(_ && _)
  }
  def fire() = {
    rvs.reduce(_ && _)
  }
}

object ProtoaccLogger {

  def logInfo(format: String, args: Bits*)(implicit p: Parameters): Unit = {
    val loginfo_cycles = RegInit(0.U(64.W))
    loginfo_cycles := loginfo_cycles + 1.U

    if (p(CoreKey).ProtoAccelPrintfEnable) {
      printf("cy: %d, ", loginfo_cycles)
      printf(Printable.pack(format, args:_*))
    } else {
      printf("cy: %d, ", loginfo_cycles)
      printf(Printable.pack(format, args:_*))
    }
  }

  def logWaveStyle(format: String, args: Bits*)(implicit p: Parameters): Unit = {

  }

}

object ProtoaccParams {
  val MAX_NESTED_LEVELS = 25
  val MAX_NESTED_LEVELS_WIDTH = log2Up(MAX_NESTED_LEVELS) + 1

}
