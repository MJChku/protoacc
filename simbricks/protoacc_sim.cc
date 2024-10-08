/*
 * Copyright 2024 Max Planck Institute for Software Systems, and
 * National University of Singapore
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files (the
 * "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish,
 * distribute, sublicense, and/or sell copies of the Software, and to
 * permit persons to whom the Software is furnished to do so, subject to
 * the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <signal.h>
#include <verilated.h>

#include <cassert>

#include "VVTAShell.h"
#include "simbricks/pcie/proto.h"

// #define TRACE_ENABLED 
// #define MMIO_DEBUG 1

#include <iostream>
#ifdef TRACE_ENABLED
#include <verilated_vcd_c.h>
#endif

#include "vta_simbricks.hh"

namespace {
uint64_t clock_period = 10 * 1000ULL;  // 10ns -> 100MHz
size_t dev_mem_size = 1024UL * 1024 * 1024;

volatile int exiting = 0;
bool terminated = false;
uint64_t main_time = 0;
struct SimbricksNicIf nicif {};

VTAMemReader *mem_reader;
void *dev_mem;

VVTAShell *shell;
#ifdef TRACE_ENABLED
VerilatedVcdC *trace;
#endif

volatile union SimbricksProtoPcieD2H *d2h_alloc();

void sigint_handler(int dummy) {
  exiting = 1;
}

void reset_inputs(VVTAShell &top) {
  top.clock = 0;
  top.reset = 0;

  top.io_host_aw_valid = 0;
  top.io_host_aw_bits_addr = 0;
  top.io_host_w_valid = 0;
  top.io_host_w_bits_data = 0;
  top.io_host_w_bits_strb = 0;
  top.io_host_b_ready = 0;
  top.io_host_ar_valid = 0;
  top.io_host_ar_bits_addr = 0;
  top.io_host_r_ready = 0;

  top.io_mem_aw_ready = 0;
  top.io_mem_w_ready = 0;
  top.io_mem_b_valid = 0;
  top.io_mem_b_bits_resp = 0;
  top.io_mem_b_bits_id = 0;
  top.io_mem_b_bits_user = 0;
  top.io_mem_ar_ready = 0;
  top.io_mem_r_valid = 0;
  top.io_mem_r_bits_data = 0;
  top.io_mem_r_bits_resp = 0;
  top.io_mem_r_bits_last = 0;
  top.io_mem_r_bits_id = 0;
  top.io_mem_r_bits_user = 0;
}

void sigusr1_handler(int dummy) {
  fprintf(stderr, "main_time = %lu\n", main_time);
}

struct MMIOOp {
  uint64_t id = 0;
  uint64_t addr = 0;
  uint64_t value = 0;
  size_t len = 0;
  bool isWrite = false;
  bool isPosted = false;
};

void mmio_done(MMIOOp *mmio_op);

class AxiLiteManager {
 protected:
  VVTAShell &top_;
  std::deque<MMIOOp *> queue_{};
  MMIOOp *rCur_ = nullptr;
  MMIOOp *wCur_ = nullptr;

  /* ack on read address channel */
  bool rAAck_ = false;
  /* ack on write address channel */
  bool wAAck_ = false;
  /* ack on write data channel */
  bool wDAck_ = false;

  uint64_t tmp[20] = {0};
  bool tmp_active[20] = {0};

 public:
  explicit AxiLiteManager(VVTAShell &top) : top_(top) {
  }

  // all the signals we saw in the step function should be right before
  // the rising edge, all the updates we do is only seen after the rising edge.
  // so the updates we do here is like <= operator in verilog.  
#define RegWrite(idx, signal, val) tmp[idx] = val;tmp_active[idx]=true;
#define ResetTmp() for(int i=0;i<20;i++){tmp_active[i]=false;}
  void step() {
    ResetTmp();
    if (rCur_) {
      /* work on active read operation */
      if (top_.io_host_ar_valid && top_.io_host_ar_ready ) {
        /* read addr handshake is complete */
#ifdef MMIO_DEBUG
        std::cout << main_time
                  << " MMIO: AXI read addr handshake done op=" << rCur_ << "\n";
        // report_outputs(&top);
#endif
        RegWrite(0, top_.io_host_ar_valid, 0);
        rAAck_ = true;
      }
    }
    
    if (rAAck_ && top_.io_host_r_valid && top_.io_host_r_ready ) {
        assert(rAAck_);
        rCur_->value = top_.io_host_r_bits_data;
   
#ifdef MMIO_DEBUG
        std::cout << main_time << " MMIO: completed AXI read op=" << rCur_
                  << " val=" << rCur_->value << "\n";
        // report_outputs(&top);
#endif
        RegWrite(1, top_.io_host_r_ready, 0);
        mmio_done(rCur_);
        rCur_ = nullptr;
      }

    if (wCur_) {
      /* work on active write operation */
#ifdef MMIO_DEBUG
        std::cout << main_time << " MMIO: response write op=" << wCur_
                  << " bvalid" << top_.io_host_b_valid 
                  << " bready" << top_.io_host_b_ready 
                  << "\n";
        // report_outputs(&top);
#endif
    if (top_.io_host_b_valid && top_.io_host_b_ready) {
        assert(wAAck_ && wDAck_);
        // RegWrite(top_.io_host_b_ready, 0);
        mmio_done(wCur_);
        wCur_ = nullptr;
#ifdef MMIO_DEBUG
        std::cout << main_time << " MMIO: completed AXI write op=" << wCur_
                  << "\n";
        // report_outputs(&top);
#endif
      }

      if (top_.io_host_aw_valid && top_.io_host_aw_ready ) {
        /* write addr handshake is complete */
#ifdef MMIO_DEBUG
        std::cout << main_time
                  << " MMIO: AXI write addr handshake done op=" << wCur_
                  << "\n";
        // report_outputs(&top);
#endif
        RegWrite(2, top_.io_host_aw_valid, 0);
        wAAck_ = true;
        if (!wDAck_){
          RegWrite(3, top_.io_host_w_valid, 1);
        }
      }
      
      if ( wAAck_ && top_.io_host_w_valid && top_.io_host_w_ready ) {
        /* write data handshake is complete */
#ifdef MMIO_DEBUG
        std::cout << main_time
                  << " MMIO: AXI write data handshake done op=" << wCur_
                  << "\n";
        // report_outputs(&top);
#endif
        RegWrite(4, top_.io_host_w_valid, 0);
        wDAck_ = true;
        RegWrite(5, top_.io_host_b_ready, 1);

      }

    } 
    
    if (/*!top.clk &&*/ !queue_.empty() ) {
      /* issue new operation */
      MMIOOp *mmio_op = queue_.front();
      if(!mmio_op->isWrite && !rCur_){
#ifdef MMIO_DEBUG
        std::cout << main_time << " MMIO: issuing new read op on axi op=" << mmio_op
                  << "\n";
#endif
        queue_.pop_front();
        /* issue new read */
        rCur_ = mmio_op;

        RegWrite(6, top_.io_host_ar_bits_addr, rCur_->addr);
        rAAck_ = false;
        RegWrite(7, top_.io_host_ar_valid, 1);
        RegWrite(8, top_.io_host_r_ready, 1);
      }
    if (/*!top.clk &&*/mmio_op->isWrite && !wCur_) {

#ifdef MMIO_DEBUG
        std::cout << main_time << " MMIO: issuing new write op on axi op=" << mmio_op
                  << "\n";
#endif
        queue_.pop_front();
        /* issue new write */
        wCur_ = mmio_op;
        //always @(posedge clk)
        RegWrite(9, top_.io_host_aw_bits_addr, wCur_->addr);
        // wAAck_ = top_.io_host_aw_ready;
        RegWrite(10, top_.io_host_aw_valid, 1);
        RegWrite(11, top_.io_host_w_bits_data, wCur_->value);
        RegWrite(12, top_.io_host_w_bits_strb, 0xf);
        wDAck_ = false;
        wAAck_ = false;
        // make it one
        RegWrite(13, top_.io_host_w_valid, 1);
        RegWrite(14, top_.io_host_b_ready, 0);
      }
    }
  }

  void step_apply(){

#define RegApply(idx, signal) if(tmp_active[idx]==true) signal = tmp[idx]

    RegApply(6, top_.io_host_ar_bits_addr);
    RegApply(7, top_.io_host_ar_valid);
    RegApply(8, top_.io_host_r_ready);
    RegApply(9, top_.io_host_aw_bits_addr);
    RegApply(10, top_.io_host_aw_valid);
    RegApply(11, top_.io_host_w_bits_data);
    RegApply(12, top_.io_host_w_bits_strb);
    RegApply(13, top_.io_host_w_valid);
    RegApply(14, top_.io_host_b_ready);
    RegApply(0, top_.io_host_ar_valid);
    RegApply(1, top_.io_host_r_ready);
    RegApply(2, top_.io_host_aw_valid);
    RegApply(3, top_.io_host_w_valid);
    RegApply(4, top_.io_host_w_valid);
    RegApply(5, top_.io_host_b_ready);
  }

  void issueRead(uint64_t req_id, uint64_t addr, size_t len) {
    MMIOOp *mmio_op = new MMIOOp{};
#ifdef MMIO_DEBUG
    std::cout << main_time << " MMIO: read id=" << req_id
              << " addr=" << std::hex << addr << " len=" << len
              << " op=" << mmio_op << "\n";
#endif
    mmio_op->id = req_id;
    mmio_op->addr = addr;
    mmio_op->len = len;
    mmio_op->isWrite = false;
    queue_.push_back(mmio_op);
  }

  void issueWrite(uint64_t req_id, uint64_t addr, size_t len, uint64_t val,
                  bool isPosted) {
    MMIOOp *mmio_op = new MMIOOp{};
#ifdef MMIO_DEBUG
    std::cout << main_time << " MMIO: write id=" << req_id
              << " addr=" << std::hex << addr << " len=" << len
              << " val=" << val << " op=" << mmio_op << "\n";
#endif
    mmio_op->id = req_id;
    mmio_op->addr = addr;
    mmio_op->len = len;
    mmio_op->value = val;
    mmio_op->isWrite = true;
    mmio_op->isPosted = isPosted;
    queue_.push_back(mmio_op);
  }
};

void mmio_done(MMIOOp *mmio_op) {
  if (!mmio_op->isWrite || !mmio_op->isPosted) {
    volatile union SimbricksProtoPcieD2H *msg = d2h_alloc();
    volatile struct SimbricksProtoPcieD2HReadcomp *readcomp;
    volatile struct SimbricksProtoPcieD2HWritecomp *writecomp;

    if (!msg)
      throw "completion alloc failed";

    if (mmio_op->isWrite) {
      writecomp = &msg->writecomp;
      writecomp->req_id = mmio_op->id;

      SimbricksPcieIfD2HOutSend(&nicif.pcie, msg,
                                SIMBRICKS_PROTO_PCIE_D2H_MSG_WRITECOMP);
    } else if (!mmio_op->isWrite) {
      readcomp = &msg->readcomp;
      // NOLINTNEXTLINE(google-readability-casting)
      memcpy((void *)readcomp->data, &mmio_op->value, mmio_op->len);
      readcomp->req_id = mmio_op->id;

      SimbricksPcieIfD2HOutSend(&nicif.pcie, msg,
                                SIMBRICKS_PROTO_PCIE_D2H_MSG_READCOMP);
    }
  }

  delete mmio_op;
}

void h2d_read(AxiLiteManager &mmio,
              volatile struct SimbricksProtoPcieH2DRead *read) {
  // std::cout << "got read " << read->offset << "\n";
  if (read->bar == 0) {
    /*printf("read(bar=%u, off=%lu, len=%u) = %lu\n", read->bar, read->offset,
            read->len, val);*/
    mmio.issueRead(read->req_id, read->offset, read->len);
  } else if (read->bar == 2) {
    volatile union SimbricksProtoPcieD2H *msg = d2h_alloc();
    volatile struct SimbricksProtoPcieD2HReadcomp *readcomp;

    if (!msg)
      throw "completion alloc failed";

    readcomp = &msg->readcomp;
    // NOLINTNEXTLINE(google-readability-casting)
    memcpy((void *)readcomp->data,
           static_cast<uint8_t *>(dev_mem) + read->offset, read->len);
    readcomp->req_id = read->req_id;

    SimbricksPcieIfD2HOutSend(&nicif.pcie, msg,
                              SIMBRICKS_PROTO_PCIE_D2H_MSG_READCOMP);
  } else {
    throw "unexpected bar";
  }
}

void h2d_write(AxiLiteManager &mmio,
               volatile struct SimbricksProtoPcieH2DWrite *write,
               bool isPosted) {
  // std::cout << "got write " << write->offset << " = " << val << "\n";

  if (write->bar == 0) {
    uint64_t val = 0;
    // NOLINTNEXTLINE(google-readability-casting)
    memcpy(&val, (void *)write->data, write->len);
    mmio.issueWrite(write->req_id, write->offset, write->len, val, isPosted);
  } else if (write->bar == 2) {
    // NOLINTNEXTLINE(google-readability-casting)
    memcpy(static_cast<uint8_t *>(dev_mem) + write->offset, (void *)write->data,
           write->len);

    if (isPosted) {
      return;
    }

    volatile union SimbricksProtoPcieD2H *msg = d2h_alloc();
    volatile struct SimbricksProtoPcieD2HWritecomp *writecomp;

    writecomp = &msg->writecomp;
    writecomp->req_id = write->req_id;

    SimbricksPcieIfD2HOutSend(&nicif.pcie, msg,
                              SIMBRICKS_PROTO_PCIE_D2H_MSG_WRITECOMP);
  } else {
    throw "unexpected bar";
  }
}

void h2d_readcomp(volatile struct SimbricksProtoPcieH2DReadcomp *readcomp) {
  VTAMemReader::AXIOperationT *axi_op =
      // NOLINTNEXTLINE(performance-no-int-to-ptr)
      reinterpret_cast<VTAMemReader::AXIOperationT *>(readcomp->req_id);
  memcpy(axi_op->buf, const_cast<uint8_t *>(readcomp->data), axi_op->len);

  mem_reader->readDone(axi_op);
}

void h2d_writecomp(volatile struct SimbricksProtoPcieH2DWritecomp *writecomp) {
  // std::cout << "dma write completed" << "\n";
}

void poll_h2d(AxiLiteManager &mmio) {
  volatile union SimbricksProtoPcieH2D *msg =
      SimbricksPcieIfH2DInPoll(&nicif.pcie, main_time);
  uint16_t type;

  if (msg == nullptr)
    return;

  type = SimbricksPcieIfH2DInType(&nicif.pcie, msg);

  // std::cerr << "poll_h2d: polled type=" << (int) t << "\n";
  switch (type) {
    case SIMBRICKS_PROTO_PCIE_H2D_MSG_READ:
      h2d_read(mmio, &msg->read);
      break;

    case SIMBRICKS_PROTO_PCIE_H2D_MSG_WRITE:
      h2d_write(mmio, &msg->write, false);
      break;

    case SIMBRICKS_PROTO_PCIE_H2D_MSG_WRITE_POSTED:
      h2d_write(mmio, &msg->write, true);
      break;

    case SIMBRICKS_PROTO_PCIE_H2D_MSG_READCOMP:
      h2d_readcomp(&msg->readcomp);
      break;

    case SIMBRICKS_PROTO_PCIE_H2D_MSG_WRITECOMP:
      h2d_writecomp(&msg->writecomp);
      break;

    case SIMBRICKS_PROTO_PCIE_H2D_MSG_DEVCTRL:
    case SIMBRICKS_PROTO_MSG_TYPE_SYNC:
      break;

    case SIMBRICKS_PROTO_MSG_TYPE_TERMINATE:
      std::cerr << "poll_h2d: peer terminated"
                << "\n";
      terminated = true;
      break;

    default:
      std::cerr << "poll_h2d: unsupported type=" << type << "\n";
  }

  SimbricksPcieIfH2DInDone(&nicif.pcie, msg);
}

volatile union SimbricksProtoPcieD2H *d2h_alloc() {
  return SimbricksPcieIfD2HOutAlloc(&nicif.pcie, main_time);
}

}  // namespace

void VTAMemReader::doRead(AXIOperationT *axi_op) {
  volatile union SimbricksProtoPcieD2H *msg = d2h_alloc();
  if (!msg)
    throw "dma read alloc failed";

  volatile struct SimbricksProtoPcieD2HRead *read = &msg->read;
  // NOLINTNEXTLINE(google-readability-casting)
  read->req_id = (uintptr_t)axi_op;
  read->offset = axi_op->addr;
  read->len = axi_op->len;

  assert(SimbricksPcieIfH2DOutMsgLen(&nicif.pcie) -
                 sizeof(SimbricksProtoPcieH2DReadcomp) >=
             axi_op->len &&
         "Read response can't fit the required number of bytes");

  SimbricksPcieIfD2HOutSend(&nicif.pcie, msg,
                            SIMBRICKS_PROTO_PCIE_D2H_MSG_READ);
}

void VTAMemWriter::doWrite(AXIOperationT *axi_op) {
  volatile union SimbricksProtoPcieD2H *msg = d2h_alloc();
  if (!msg)
    throw "dma read alloc failed";

  volatile struct SimbricksProtoPcieD2HWrite *write = &msg->write;
  // NOLINTNEXTLINE(google-readability-casting)
  write->req_id = (uintptr_t)axi_op;
  write->offset = axi_op->addr;
  write->len = axi_op->len;

  assert(SimbricksPcieIfD2HOutMsgLen(&nicif.pcie) -
                 sizeof(SimbricksProtoPcieD2HWrite) >=
             axi_op->len &&
         "Write message can't fit the required number of bytes");

  // NOLINTNEXTLINE(google-readability-casting)
  memcpy((void *)write->data, axi_op->buf, axi_op->len);
  SimbricksPcieIfD2HOutSend(&nicif.pcie, msg,
                            SIMBRICKS_PROTO_PCIE_D2H_MSG_WRITE);

  writeDone(axi_op);
}

int protoacc_sim_run(
    uint64_t descriptor_table_addr,
    uint64_t cpp_obj_addr,
    uint64_t has_bits_base_offset_only,
    uint64_t stringalloc_region_addr_tail,
    uint64_t stringptr_region_addr,
    uint32_t min_fieldno,
    uint32_t max_fieldno
) {
  /* initialize verilated model */
  shell = new VVTAShell;

#ifdef TRACE_ENABLED
  Verilated::traceEverOn(true);
  trace = new VerilatedVcdC;
  shell->trace(trace, 99);
  trace->open("debug.vcd");
#endif

  AxiLiteManager mmio{*shell};
  mem_reader = new VTAMemReader{*shell};
  VTAMemWriter mem_writer{*shell};

  // reset HW
  reset_inputs(*shell);
  for (int i = 0; i < 10; i++) {
    shell->reset = 1;
    shell->clock = 0;
    main_time += clock_period / 2;
    shell->eval();
#ifdef TRACE_ENABLED
    trace->dump(main_time);
#endif
    shell->clock = 1;
    main_time += clock_period / 2;
    shell->eval();
#ifdef TRACE_ENABLED
    trace->dump(main_time);
#endif
  }
  shell->reset = 0;
  
    /* falling edge */
    shell->clock = 0;
    main_time += clock_period / 2;
    shell->eval();
#ifdef TRACE_ENABLED
    trace->dump(main_time);
#endif

    /* raising edge */
    shell->clock = 1;
    main_time += clock_period / 2;
    mmio.step();
    mem_writer.step(main_time);
    mem_reader->step(main_time);
    shell->eval();
    mmio.step_apply();
    mem_reader->step_apply();
    mem_writer.step_apply();
#ifdef TRACE_ENABLED
    trace->dump(main_time);
#endif
  }

#ifdef TRACE_ENABLED
  trace->dump(main_time + 1);
  trace->close();
  delete trace;
#endif
  shell->final();
  delete shell;
  return 0;
}