package vta.core

import Chisel._
import chisel3.{Printable}
import vta.util.config._
import vta.util.genericbundle._
import vta.interface.axi._
import vta.shell._

// class CommandRouterSerializer()(implicit p: Parameters) extends Module {

//   val FUNCT_SFENCE = UInt(0)
//   val FUNCT_HASBITS_SETUP_INFO = UInt(1)
//   val FUNCT_DO_PROTO_SERIALIZE = UInt(2)


//   val FUNCT_MEM_SETUP = UInt(3)
//   val FUNCT_CHECK_COMPLETION = UInt(4)

//   val io = IO(new Bundle{
//     val rocc_in = Decoupled(new RoCCCommand).flip
//     val rocc_out = Decoupled(new RoCCResponse)

//     val sfence_out = Bool(OUTPUT)


//     val serializer_info_bundle_out = Decoupled(new SerializerInfoBundle)
//     val dmem_status_out = Valid(new RoCCCommand)

//     val stringalloc_region_addr_tail = Valid(UInt(64.W))
//     val stringptr_region_addr = Valid(UInt(64.W))

//     val no_writes_inflight = Input(Bool())
//     val completed_toplevel_bufs = Input(UInt(64.W))

//   })

//   val track_number_dispatched_parse_commands = RegInit(0.U(64.W))
//   when (io.rocc_in.fire()) {
//     when (io.rocc_in.bits.inst.funct === FUNCT_DO_PROTO_SERIALIZE) {
//       val next_track_number_dispatched_parse_commands = track_number_dispatched_parse_commands + 1.U
//       track_number_dispatched_parse_commands := next_track_number_dispatched_parse_commands
//       ProtoaccLogger.logInfo("dispatched bufs: current 0x%x, next 0x%x\n",
//         track_number_dispatched_parse_commands,
//         next_track_number_dispatched_parse_commands)
//     }
//   }

//   when (io.rocc_in.fire()) {
//     ProtoaccLogger.logInfo("gotcmd funct %x, rd %x, rs1val %x, rs2val %x\n", io.rocc_in.bits.inst.funct, io.rocc_in.bits.inst.rd, io.rocc_in.bits.rs1, io.rocc_in.bits.rs2)
//   }

//   io.dmem_status_out.bits <> io.rocc_in.bits
//   io.dmem_status_out.valid := io.rocc_in.fire()

//   val hasbits_setup_info_out_queue = Module(new Queue(new RoCCCommand, 2))
//   val do_proto_serialize_out_queue = Module(new Queue(new RoCCCommand, 2))


//   val ser_out_fire = DecoupledHelper(
//     hasbits_setup_info_out_queue.io.deq.valid,
//     do_proto_serialize_out_queue.io.deq.valid,
//     io.serializer_info_bundle_out.ready
//   )
//   hasbits_setup_info_out_queue.io.deq.ready := ser_out_fire.fire(hasbits_setup_info_out_queue.io.deq.valid)
//   do_proto_serialize_out_queue.io.deq.ready := ser_out_fire.fire(do_proto_serialize_out_queue.io.deq.valid)
//   io.serializer_info_bundle_out.valid := ser_out_fire.fire(io.serializer_info_bundle_out.ready)

//   io.serializer_info_bundle_out.bits.has_bits_base_offset_only := hasbits_setup_info_out_queue.io.deq.bits.rs1
//   io.serializer_info_bundle_out.bits.min_fieldno := hasbits_setup_info_out_queue.io.deq.bits.rs2 >> 32
//   io.serializer_info_bundle_out.bits.max_fieldno := hasbits_setup_info_out_queue.io.deq.bits.rs2
//   io.serializer_info_bundle_out.bits.descriptor_table_addr := do_proto_serialize_out_queue.io.deq.bits.rs1
//   io.serializer_info_bundle_out.bits.cpp_obj_addr := do_proto_serialize_out_queue.io.deq.bits.rs2

//   val current_funct = io.rocc_in.bits.inst.funct

//   val sfence_fire = DecoupledHelper(
//     io.rocc_in.valid,
//     current_funct === FUNCT_SFENCE
//   )
//   io.sfence_out := sfence_fire.fire()

//   val hasbits_info_fire = DecoupledHelper(
//     io.rocc_in.valid,
//     hasbits_setup_info_out_queue.io.enq.ready,
//     current_funct === FUNCT_HASBITS_SETUP_INFO
//   )

//   hasbits_setup_info_out_queue.io.enq.valid := hasbits_info_fire.fire(hasbits_setup_info_out_queue.io.enq.ready)

//   val do_proto_serialize_fire = DecoupledHelper(
//     io.rocc_in.valid,
//     do_proto_serialize_out_queue.io.enq.ready,
//     current_funct === FUNCT_DO_PROTO_SERIALIZE
//   )

//   do_proto_serialize_out_queue.io.enq.valid := do_proto_serialize_fire.fire(do_proto_serialize_out_queue.io.enq.ready)

//   hasbits_setup_info_out_queue.io.enq.bits <> io.rocc_in.bits
//   do_proto_serialize_out_queue.io.enq.bits <> io.rocc_in.bits

//   val do_alloc_region_addr_fire = DecoupledHelper(
//     io.rocc_in.valid,
//     current_funct === FUNCT_MEM_SETUP
//   )

//   io.stringalloc_region_addr_tail.bits := io.rocc_in.bits.rs1
//   io.stringalloc_region_addr_tail.valid := do_alloc_region_addr_fire.fire()

//   io.stringptr_region_addr.bits := io.rocc_in.bits.rs2
//   io.stringptr_region_addr.valid := do_alloc_region_addr_fire.fire()


//   val do_check_completion_fire = DecoupledHelper(
//     io.rocc_in.valid,
//     current_funct === FUNCT_CHECK_COMPLETION,
//     io.no_writes_inflight,
//     io.completed_toplevel_bufs === track_number_dispatched_parse_commands,
//     io.rocc_out.ready
//   )

//   when (io.rocc_in.valid && current_funct === FUNCT_CHECK_COMPLETION) {
//     ProtoaccLogger.logInfo("[commandrouter] WAITING FOR COMPLETION. no_writes_inflight 0x%d, completed 0x%x, dispatched 0x%x, rocc_out.ready 0x%x\n",
//       io.no_writes_inflight, io.completed_toplevel_bufs, track_number_dispatched_parse_commands, io.rocc_out.ready)
//   }

//   io.rocc_out.valid := do_check_completion_fire.fire(io.rocc_out.ready)
//   io.rocc_out.bits.rd := io.rocc_in.bits.inst.rd
//   io.rocc_out.bits.data := track_number_dispatched_parse_commands


//   io.rocc_in.ready := sfence_fire.fire(io.rocc_in.valid) || hasbits_info_fire.fire(io.rocc_in.valid) || do_proto_serialize_fire.fire(io.rocc_in.valid) || do_alloc_region_addr_fire.fire(io.rocc_in.valid) || do_check_completion_fire.fire(io.rocc_in.valid)

// }


// fix later
// class CommandRouterSerializer()(implicit p: Parameters) extends Module {
//   val io = IO(new Bundle {
//     val vcr = new VCRClient

//     // Outputs to other modules
//     val serializer_info_bundle_out = Decoupled(new SerializerInfoBundle)

//     val stringalloc_region_addr_tail = Valid(UInt(64.W))
//     val stringptr_region_addr = Valid(UInt(64.W))
//     // Additional signals
//     val no_writes_inflight = Input(Bool())
//     val completed_toplevel_bufs = Input(UInt(64.W))
//   })

//    // Operation control logic
//   val operation_in_progress = RegInit(false.B)
//   val track_number_dispatched_parse_commands = RegInit(0.U(64.W))


//   // Create a Bundle for the fields
//   class DescriptorInfo extends Bundle {
//     val descriptor_table_addr = UInt(64.W)
//     val cpp_obj_addr = UInt(64.W)
//     val has_bits_base_offset_only = UInt(64.W)
//     val min_fieldno = UInt(32.W)
//     val max_fieldno = UInt(32.W)
//   }

//   // Extract control signals from VCR
//   val descriptor_table_addr        = io.vcr.ptrs(0)
//   val cpp_obj_addr                 = io.vcr.ptrs(1)
//   val has_bits_base_offset_only    = io.vcr.ptrs(2)
//   val stringalloc_region_addr_tail = io.vcr.ptrs(3)
//   val stringptr_region_addr        = io.vcr.ptrs(4)
//   val min_fieldno                  = io.vcr.vals(0)
//   val max_fieldno                  = io.vcr.vals(1)

//   // Create the structure (DescriptorInfo)
//   val descriptorInfo = Wire(new DescriptorInfo)
//   descriptorInfo.descriptor_table_addr := descriptor_table_addr
//   descriptorInfo.cpp_obj_addr := cpp_obj_addr
//   descriptorInfo.has_bits_base_offset_only := has_bits_base_offset_only
//   descriptorInfo.min_fieldno := min_fieldno
//   descriptorInfo.max_fieldno := max_fieldno

//   // Create a Queue with depth 2
//   val descriptorQueue = Module(new Queue(new DescriptorInfo, 2))

//   // Default values for the queue
//   descriptorQueue.io.enq.valid := false.B
//   descriptorQueue.io.enq.bits := descriptorInfo

//   // Enqueue the structure when launch is true and there's space in the queue
//   when(io.vcr.launch && descriptorQueue.io.enq.ready && !operation_in_progress) {
//     descriptorQueue.io.enq.valid := true.B
//   }

//   // Output logic (drive outputs from the queue when there's valid data)
//   io.stringalloc_region_addr_tail.valid := descriptorQueue.io.deq.valid
//   io.stringptr_region_addr.valid := descriptorQueue.io.deq.valid
  
//   io.serializer_info_bundle_out.valid := descriptorQueue.io.deq.valid
//   io.serializer_info_bundle_out.bits.has_bits_base_offset_only := descriptorQueue.io.deq.bits.has_bits_base_offset_only
//   io.serializer_info_bundle_out.bits.min_fieldno := descriptorQueue.io.deq.bits.min_fieldno
//   io.serializer_info_bundle_out.bits.max_fieldno := descriptorQueue.io.deq.bits.max_fieldno
//   io.serializer_info_bundle_out.bits.descriptor_table_addr := descriptorQueue.io.deq.bits.descriptor_table_addr
//   io.serializer_info_bundle_out.bits.cpp_obj_addr := descriptorQueue.io.deq.bits.cpp_obj_addr

//   // Dequeue when the output is ready
//   descriptorQueue.io.deq.ready := io.serializer_info_bundle_out.ready

//   // Assign values to outputs
//   io.stringalloc_region_addr_tail.bits := stringalloc_region_addr_tail
//   io.stringptr_region_addr.bits := stringptr_region_addr

 
//   when(io.vcr.launch && !operation_in_progress) {
//     operation_in_progress := true.B
//     track_number_dispatched_parse_commands := track_number_dispatched_parse_commands + 1.U
//     ProtoaccLogger.logInfo("track_number_dispatched_parse_commands %d\n", track_number_dispatched_parse_commands)
//   }

//   val do_check_completion_fire = DecoupledHelper(
//     operation_in_progress,
//     io.no_writes_inflight,
//     io.completed_toplevel_bufs === track_number_dispatched_parse_commands
//   )

//   val operation_done = do_check_completion_fire.fire()

//   when(operation_done) {
//     operation_in_progress := false.B
//   }

//   // Signal completion to VCR
//   io.vcr.finish := operation_done

//   // Optional logging
//   when(io.vcr.launch && !operation_in_progress) {
//     ProtoaccLogger.logInfo("VCR Launch signal received, starting operation\n")
//   }
//   when(operation_done) {
//     ProtoaccLogger.logInfo("Operation completed\n")
//   }
// }

class CommandRouterSerializer()(implicit p: Parameters) extends Module {
  val io = IO(new Bundle {
    val vcr = new VCRClient

    // Outputs to other modules
    val serializer_info_bundle_out = Decoupled(new SerializerInfoBundle)

    val stringalloc_region_addr_tail = Valid(UInt(64.W))
    val stringptr_region_addr = Valid(UInt(64.W))
    // Additional signals
    val no_writes_inflight = Input(Bool())
    val completed_toplevel_bufs = Input(UInt(64.W))
  })

  // Track the number of dispatched commands
  val track_number_dispatched_parse_commands = RegInit(0.U(64.W))

  // Create a Bundle for the fields
  class DescriptorInfo extends Bundle {
    val descriptor_table_addr = UInt(64.W)
    val cpp_obj_addr = UInt(64.W)
    val has_bits_base_offset_only = UInt(64.W)
    val min_fieldno = UInt(32.W)
    val max_fieldno = UInt(32.W)
  }

  // Extract control signals from VCR
  val descriptor_table_addr        = io.vcr.ptrs(0)
  val cpp_obj_addr                 = io.vcr.ptrs(1)
  val has_bits_base_offset_only    = io.vcr.ptrs(2)
  val stringalloc_region_addr_tail = io.vcr.ptrs(3)
  val stringptr_region_addr        = io.vcr.ptrs(4)
  val min_fieldno                  = io.vcr.vals(0)
  val max_fieldno                  = io.vcr.vals(1)

  // Create the structure (DescriptorInfo)
  val descriptorInfo = Wire(new DescriptorInfo)
  descriptorInfo.descriptor_table_addr := descriptor_table_addr
  descriptorInfo.cpp_obj_addr := cpp_obj_addr
  descriptorInfo.has_bits_base_offset_only := has_bits_base_offset_only
  descriptorInfo.min_fieldno := min_fieldno
  descriptorInfo.max_fieldno := max_fieldno

  // Create a Queue with depth 200
  val descriptorQueue = Module(new Queue(new DescriptorInfo, 200))
  
  val launch_consumed = RegInit(false.B)

  // Enqueue logic
  descriptorQueue.io.enq.valid := io.vcr.launch & !launch_consumed
  descriptorQueue.io.enq.bits := descriptorInfo

  io.vcr.launch_consumed := launch_consumed
  
  when(io.vcr.launch === false.B){
    launch_consumed := false.B
  }

  // Enqueue the structure when launch is true and there's space in the queue
  when(descriptorQueue.io.enq.fire){
    launch_consumed := true.B
    track_number_dispatched_parse_commands := track_number_dispatched_parse_commands + 1.U
    ProtoaccLogger.logInfo2("track_number_dispatched_parse_commands %d\n", track_number_dispatched_parse_commands)
  }

  // Output logic (drive outputs from the queue when there's valid data)
  io.stringalloc_region_addr_tail.valid := io.vcr.launch2
  io.stringptr_region_addr.valid := io.vcr.launch2

  io.serializer_info_bundle_out.valid := descriptorQueue.io.deq.valid
  io.serializer_info_bundle_out.bits.has_bits_base_offset_only := descriptorQueue.io.deq.bits.has_bits_base_offset_only
  io.serializer_info_bundle_out.bits.min_fieldno := descriptorQueue.io.deq.bits.min_fieldno
  io.serializer_info_bundle_out.bits.max_fieldno := descriptorQueue.io.deq.bits.max_fieldno
  io.serializer_info_bundle_out.bits.descriptor_table_addr := descriptorQueue.io.deq.bits.descriptor_table_addr
  io.serializer_info_bundle_out.bits.cpp_obj_addr := descriptorQueue.io.deq.bits.cpp_obj_addr

  // Dequeue when the output is ready
  descriptorQueue.io.deq.ready := io.serializer_info_bundle_out.ready

  // Assign values to outputs
  io.stringalloc_region_addr_tail.bits := stringalloc_region_addr_tail
  io.stringptr_region_addr.bits := stringptr_region_addr
  // Optional logging
  when(io.vcr.launch2) {
    // ProtoaccLogger.logInfo("VCR Fill string output signal received\n")
  }
  // Optional logging
  when(io.vcr.launch) {
    ProtoaccLogger.logInfo2("VCR Launch signal received, starting operation\n")
  }

  // Completion logic
  val operation_in_progress = track_number_dispatched_parse_commands =/= io.completed_toplevel_bufs
  val operation_done = !operation_in_progress && io.no_writes_inflight && track_number_dispatched_parse_commands > 0.U 
  
  val completed_reg = RegInit(0.U(64.W))
  when(io.completed_toplevel_bufs =/= completed_reg){
    ProtoaccLogger.logInfo2("Completed level 0x%x\n", io.completed_toplevel_bufs)
    completed_reg := io.completed_toplevel_bufs
  }
  io.vcr.ecnt(0).valid := operation_done
  io.vcr.ecnt(0).bits := completed_reg(31, 0)

  when(operation_done){
    // io.vcr.completed_msg := completed_reg(31, 0)
    ProtoaccLogger.logInfo("All operations completed\n")
  }
  // Signal completion to VCR
  // io.vcr.finish := operation_done
}
