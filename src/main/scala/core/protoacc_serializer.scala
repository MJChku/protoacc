package vta.core

import Chisel._
import chisel3.{Printable}
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._
import vta.shell._

// class ProtoAccelSerializer(opcodes: OpcodeSet)(implicit p: Parameters) extends LazyRoCC(
//     opcodes = opcodes, nPTWPorts = 9) {
//   override lazy val module = new ProtoAccelSerializerImp(this)


//   val tapeout = true
//   val roccTLNode = if (tapeout) atlNode else tlNode


//   val mem_descr1 = LazyModule(new L1MemHelper(printInfo="[m_serdescr1]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_descr1.masterNode
//   val mem_descr2 = LazyModule(new L1MemHelper(printInfo="[m_serdescr2]", queueRequests=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_descr2.masterNode

//   val mem_serfieldhandler1 = LazyModule(new L1MemHelper(printInfo="[m_serfieldhandler1]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serfieldhandler1.masterNode
//   val mem_serfieldhandler2 = LazyModule(new L1MemHelper(printInfo="[m_serfieldhandler2]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serfieldhandler2.masterNode
//   val mem_serfieldhandler3 = LazyModule(new L1MemHelper(printInfo="[m_serfieldhandler3]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serfieldhandler3.masterNode
//   val mem_serfieldhandler4 = LazyModule(new L1MemHelper(printInfo="[m_serfieldhandler4]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serfieldhandler4.masterNode
//   val mem_serfieldhandler5 = LazyModule(new L1MemHelper(printInfo="[m_serfieldhandler5]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serfieldhandler5.masterNode
//   val mem_serfieldhandler6 = LazyModule(new L1MemHelper(printInfo="[m_serfieldhandler6]", queueRequests=true, queueResponses=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serfieldhandler6.masterNode

//   val mem_serwriter = LazyModule(new L1MemHelperWriteFast(printInfo="[m_serwriter]", queueRequests=true))
//   roccTLNode := TLBuffer.chainNode(1) := mem_serwriter.masterNode
// }

// class ProtoAccelSerializerImp(outer: ProtoAccelSerializer)(implicit p: Parameters) extends LazyRoCCModuleImp(outer)
// with MemoryOpConstants {

//   io.interrupt := Bool(false)

//   val cmd_router = Module(new CommandRouterSerializer)
//   cmd_router.io.rocc_in <> io.cmd
//   io.resp <> cmd_router.io.rocc_out

//   io.mem.req.valid := false.B
//   io.mem.s1_kill := false.B
//   io.mem.s2_kill := false.B
//   io.mem.keep_clock_enabled := true.B

//   val ser_descr_tab = Module(new SerDescriptorTableHandler)
//   mem_descr1.io.userif <> ser_descr_tab.io.l2helperUser1
//   mem_descr1.module.io.sfence <> cmd_router.io.sfence_out
//   mem_descr1.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_descr1.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(0) <> mem_descr1.module.io.ptw

//   mem_descr2.io.userif <> ser_descr_tab.io.l2helperUser2
//   mem_descr2.module.io.sfence <> cmd_router.io.sfence_out
//   mem_descr2.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_descr2.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(1) <> mem_descr2.module.io.ptw

//   ser_descr_tab.io.serializer_cmd_in <> cmd_router.io.serializer_info_bundle_out

//   val descr_to_fieldhandler_router = Module(new FieldDispatchRouter(6))
//   descr_to_fieldhandler_router.io.fields_req_in <> ser_descr_tab.io.ser_field_handler_output
//   val fieldhandler_to_memwriter_arbiter = Module(new MemWriteArbiter(6))

//   val ser_field_handler1 = Module(new SerFieldHandler("[serfieldhandler1]"))
//   ser_field_handler1.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(0)
//   mem_serfieldhandler1.io.userif <> ser_field_handler1.io.memread
//   mem_serfieldhandler1.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serfieldhandler1.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serfieldhandler1.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(2) <> mem_serfieldhandler1.module.io.ptw
//   fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(0) <> ser_field_handler1.io.writer_output

//   val ser_field_handler2 = Module(new SerFieldHandler("[serfieldhandler2]"))
//   ser_field_handler2.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(1)
//   mem_serfieldhandler2.io.userif <> ser_field_handler2.io.memread
//   mem_serfieldhandler2.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serfieldhandler2.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serfieldhandler2.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(4) <> mem_serfieldhandler2.module.io.ptw
//   fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(1) <> ser_field_handler2.io.writer_output

//   val ser_field_handler3 = Module(new SerFieldHandler("[serfieldhandler3]"))
//   ser_field_handler3.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(3-1)
//   mem_serfieldhandler3.io.userif <> ser_field_handler3.io.memread
//   mem_serfieldhandler3.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serfieldhandler3.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serfieldhandler3.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(5) <> mem_serfieldhandler3.module.io.ptw
//   fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(3-1) <> ser_field_handler3.io.writer_output

//   val ser_field_handler4 = Module(new SerFieldHandler("[serfieldhandler4]"))
//   ser_field_handler4.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(4-1)
//   mem_serfieldhandler4.io.userif <> ser_field_handler4.io.memread
//   mem_serfieldhandler4.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serfieldhandler4.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serfieldhandler4.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(6) <> mem_serfieldhandler4.module.io.ptw
//   fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(4-1) <> ser_field_handler4.io.writer_output

//   val ser_field_handler5 = Module(new SerFieldHandler("[serfieldhandler5]"))
//   ser_field_handler5.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(5-1)
//   mem_serfieldhandler5.io.userif <> ser_field_handler5.io.memread
//   mem_serfieldhandler5.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serfieldhandler5.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serfieldhandler5.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(7) <> mem_serfieldhandler5.module.io.ptw
//   fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(5-1) <> ser_field_handler5.io.writer_output

//   val ser_field_handler6 = Module(new SerFieldHandler("[serfieldhandler6]"))
//   ser_field_handler6.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(6-1)
//   mem_serfieldhandler6.io.userif <> ser_field_handler6.io.memread
//   mem_serfieldhandler6.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serfieldhandler6.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serfieldhandler6.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(8) <> mem_serfieldhandler6.module.io.ptw
//   fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(6-1) <> ser_field_handler6.io.writer_output

//   val ser_memwriter = Module(new SerMemwriter)
//   ser_memwriter.io.stringobj_output_addr <> cmd_router.io.stringalloc_region_addr_tail
//   ser_memwriter.io.string_ptr_output_addr <> cmd_router.io.stringptr_region_addr
//   ser_memwriter.io.memwrites_in <> fieldhandler_to_memwriter_arbiter.io.write_reqs_out
//   ser_memwriter.io.l2io.resp <> mem_serwriter.io.userif.resp
//   ser_memwriter.io.l2io.no_memops_inflight := mem_serwriter.io.userif.no_memops_inflight
//   mem_serwriter.io.userif.req <> ser_memwriter.io.l2io.req
//   mem_serwriter.module.io.sfence <> cmd_router.io.sfence_out
//   mem_serwriter.module.io.status.valid := cmd_router.io.dmem_status_out.valid
//   mem_serwriter.module.io.status.bits := cmd_router.io.dmem_status_out.bits.status
//   io.ptw(3) <> mem_serwriter.module.io.ptw

//   cmd_router.io.no_writes_inflight := !(ser_memwriter.io.mem_work_outstanding)
//   cmd_router.io.completed_toplevel_bufs := ser_memwriter.io.messages_completed
//   io.busy := Bool(false)
// }

/** Core parameters */
case class CoreParams(
    ProtoAccelPrintfEnable: Boolean,
    batch: Int,
    blockOut: Int,
    blockOutFactor: Int,
    blockIn: Int,
    inpBits: Int,
    wgtBits: Int,
    uopBits: Int,
    accBits: Int,
    outBits: Int,
    uopMemDepth: Int,
    inpMemDepth: Int,
    wgtMemDepth: Int,
    accMemDepth: Int,
    outMemDepth: Int,
    instQueueEntries: Int
) {
  require(uopBits % 8 == 0,
    s"\n\n[VTA] [CoreParams] uopBits must be byte aligned\n\n")
}

case object CoreKey extends Field[CoreParams]

class ProtoAccelSerializer()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val vcr = new VCRClient
    val vme = new VMEMaster
  })

  val mem_descr1 = Module(new L1ReadMemHelper(printInfo="[m_serdescr1]", queueRequests=true, queueResponses=true))
  val mem_descr2 = Module(new L1ReadMemHelper(printInfo="[m_serdescr2]", queueRequests=true, queueResponses=true))  
  val mem_serfieldhandler1 = Module(new L1ReadMemHelper(printInfo="[m_serfieldhandler1]", queueRequests=true, queueResponses=true))
  val mem_serfieldhandler2 = Module(new L1ReadMemHelper(printInfo="[m_serfieldhandler2]", queueRequests=true, queueResponses=true))
  val mem_serfieldhandler3 = Module(new L1ReadMemHelper(printInfo="[m_serfieldhandler3]", queueRequests=true, queueResponses=true))
  val mem_serfieldhandler4 = Module(new L1ReadMemHelper(printInfo="[m_serfieldhandler4]", queueRequests=true, queueResponses=true))
  val mem_serfieldhandler5 = Module(new L1ReadMemHelper(printInfo="[m_serfieldhandler5]", queueRequests=true, queueResponses=true))
  val mem_serfieldhandler6 = Module(new L1ReadMemHelper(printInfo="[m_serfieldhandler6]", queueRequests=true, queueResponses=true))


  val mem_serwriter = Module(new L1WriteMemHelper(printInfo="[m_serwriter]", queueRequests=true))

  io.vme.rd(0) <> mem_descr1.io.vme_rd
  io.vme.rd(1) <> mem_descr2.io.vme_rd
  io.vme.rd(2) <> mem_serfieldhandler1.io.vme_rd
  io.vme.rd(3) <> mem_serfieldhandler2.io.vme_rd
  io.vme.rd(4) <> mem_serfieldhandler3.io.vme_rd
  io.vme.rd(5) <> mem_serfieldhandler4.io.vme_rd
  io.vme.rd(6) <> mem_serfieldhandler5.io.vme_rd
  io.vme.rd(7) <> mem_serfieldhandler6.io.vme_rd
  io.vme.wr(0) <> mem_serwriter.io.vme_wr

  // Instantiate your modules
  val cmd_router = Module(new CommandRouterSerializer)
  io.vcr <> cmd_router.io.vcr

  val ser_descr_tab = Module(new SerDescriptorTableHandler)
  mem_descr1.io.userif <> ser_descr_tab.io.l2helperUser1

  mem_descr2.io.userif <> ser_descr_tab.io.l2helperUser2

  ser_descr_tab.io.serializer_cmd_in <> cmd_router.io.serializer_info_bundle_out

  val descr_to_fieldhandler_router = Module(new FieldDispatchRouter(6))
  descr_to_fieldhandler_router.io.fields_req_in <> ser_descr_tab.io.ser_field_handler_output
  val fieldhandler_to_memwriter_arbiter = Module(new MemWriteArbiter(6))

  val ser_field_handler1 = Module(new SerFieldHandler("[serfieldhandler1]"))
  ser_field_handler1.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(0)
  mem_serfieldhandler1.io.userif <> ser_field_handler1.io.memread
  fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(0) <> ser_field_handler1.io.writer_output

  val ser_field_handler2 = Module(new SerFieldHandler("[serfieldhandler2]"))
  ser_field_handler2.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(1)
  mem_serfieldhandler2.io.userif <> ser_field_handler2.io.memread
  fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(1) <> ser_field_handler2.io.writer_output

  val ser_field_handler3 = Module(new SerFieldHandler("[serfieldhandler3]"))
  ser_field_handler3.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(3-1)
  mem_serfieldhandler3.io.userif <> ser_field_handler3.io.memread
  fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(3-1) <> ser_field_handler3.io.writer_output

  val ser_field_handler4 = Module(new SerFieldHandler("[serfieldhandler4]"))
  ser_field_handler4.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(4-1)
  mem_serfieldhandler4.io.userif <> ser_field_handler4.io.memread
  fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(4-1) <> ser_field_handler4.io.writer_output

  val ser_field_handler5 = Module(new SerFieldHandler("[serfieldhandler5]"))
  ser_field_handler5.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(5-1)
  mem_serfieldhandler5.io.userif <> ser_field_handler5.io.memread
  fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(5-1) <> ser_field_handler5.io.writer_output

  val ser_field_handler6 = Module(new SerFieldHandler("[serfieldhandler6]"))
  ser_field_handler6.io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(6-1)
  mem_serfieldhandler6.io.userif <> ser_field_handler6.io.memread
  fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(6-1) <> ser_field_handler6.io.writer_output

  val ser_memwriter = Module(new SerMemwriter)
  ser_memwriter.io.stringobj_output_addr <> cmd_router.io.stringalloc_region_addr_tail
  ser_memwriter.io.string_ptr_output_addr <> cmd_router.io.stringptr_region_addr
  ser_memwriter.io.memwrites_in <> fieldhandler_to_memwriter_arbiter.io.write_reqs_out
  ser_memwriter.io.l2io.resp <> mem_serwriter.io.userif.resp
  ser_memwriter.io.l2io.no_memops_inflight := mem_serwriter.io.userif.no_memops_inflight
  mem_serwriter.io.userif.req <> ser_memwriter.io.l2io.req

  cmd_router.io.no_writes_inflight := !(ser_memwriter.io.mem_work_outstanding)
  cmd_router.io.completed_toplevel_bufs := ser_memwriter.io.messages_completed
}


// class ProtoAccelSerializerImp(outer: ProtoAccelSerializer)(implicit p: Parameters) extends Module {
//   val io = IO(new Bundle {
//     // Define IOs as needed
//   })

//   // Instantiate your modules
//   val cmd_router = Module(new CommandRouterSerializer)
//   val ser_descr_tab = Module(new SerDescriptorTableHandler)
//   val descr_to_fieldhandler_router = Module(new FieldDispatchRouter(6))
//   val fieldhandler_to_memwriter_arbiter = Module(new MemWriteArbiter(6))

//   // Instantiate SerFieldHandler modules
//   val ser_field_handlers = Seq.fill(6) { Module(new SerFieldHandler) }

//   // Connect SerFieldHandlers to the FieldDispatchRouter and MemHelper
//   for (i <- 0 until 6) {
//     ser_field_handlers(i).io.ops_in <> descr_to_fieldhandler_router.io.to_fieldhandlers(i)
//     memHelper.io.userif(i) <> ser_field_handlers(i).io.memread
//     fieldhandler_to_memwriter_arbiter.io.from_fieldhandlers(i) <> ser_field_handlers(i).io.writer_output
//   }

//   // Connect mem_descr1 and mem_descr2 to ser_descr_tab
//   memHelper.io.userif(6) <> ser_descr_tab.io.l2helperUser1
//   memHelper.io.userif(7) <> ser_descr_tab.io.l2helperUser2
//   // mem_descr1.io.userif <> ser_descr_tab.io.l2helperUser1
//   // mem_descr2.io.userif <> ser_descr_tab.io.l2helperUser2

//   // Connect mem_serwriter to ser_memwriter
//   val ser_memwriter = Module(new SerMemwriter)
//   ser_memwriter.io.memwrites_in <> fieldhandler_to_memwriter_arbiter.io.write_reqs_out
//   ser_memwriter.io.l2io <> mem_serwriter.io.userif
  
//   // Continue connecting other modules as needed
// }


// class ProtoAccelSerializer()(implicit p: Parameters) extends Module {
//   val io = IO(new Bundle {
//     val vcr = new VCRClient
//     val vme = new VMEMaster
//   })
  
//   override lazy val module = new ProtoAccelSerializerImp(this)

//   // Instantiate one L1MemHelper for the field handlers
  
//   // Instantiate other MemHelpers if needed
//   // val mem_descr1 = Module(new L1MemHelper(printInfo = "[m_serdescr1]", queueRequests = true, queueResponses = true))
//   // val mem_descr2 = Module(new L1MemHelper(printInfo = "[m_serdescr2]", queueRequests = true))
//   // val mem_serwriter = Module(new L1MemHelper(printInfo = "[m_serwriter]", queueRequests = true))


//   // read only
//   val memHelper = Module(new L1MemHelper(
//     printInfo = "[m_serfieldhandler]",
//     queueRequests = true,
//     queueResponses = true,
//     numMasters = 8
//   ))

//   // write only
//   val mem_serwriter = Module(new L1MemHelper(
//     printInfo = "[m_serwriter]",
//     queueRequests = true,
//     numMasters = 1
//   ))

//   // Connect MemHelpers to the AXI bus or interconnect
//   // Assuming you have an AXI bus called axiBus
//   val axiBus = Wire(new AXIMaster(memHelper.io.axi.params))
//   axiBus <> memHelper.io.axi
//   axiBus <> mem_descr1.io.axi
//   axiBus <> mem_descr2.io.axi
//   axiBus <> mem_serwriter.io.axi

//   // Continue with the rest of your design
// }