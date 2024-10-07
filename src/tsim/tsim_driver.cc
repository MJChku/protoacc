/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

#include <bits/stdint-uintn.h>
#include <cstdio>
#include <vta/driver.h>
#include <vta/dpi/module.h>
#include "../vmem/virtual_memory.h"


namespace protoacc {
namespace tsim {

using protoacc::dpi::DPIModuleNode;
using protoacc::dpi::Module;

class DPILoader {
 public:
  ~DPILoader() {
    dpi_->SimResume();
    dpi_->SimFinish();
  }

  void Init(Module module) {
    mod_ = module;
    dpi_ = this->Get();
    printf("TSIM dpi_ simLaunch %p\n", dpi_);
    dpi_->SimLaunch();
    printf("TSIM dpi_ simwait\n");
    dpi_->SimWait();
  }

  DPIModuleNode* Get() {
    return static_cast<DPIModuleNode*>(mod_.operator->());
  }

  static DPILoader* Global() {
    static DPILoader inst;
    return &inst;
  }

  // TVM module
  Module mod_;
  // DPI Module
  DPIModuleNode* dpi_{nullptr};
};

class Device {
 public:
  Device() {
    loader_ = DPILoader::Global();
  }

  int Run(uint64_t descriptor_table_addr,
        uint64_t cpp_obj_addr,
        uint64_t has_bits_base_offset_only,
        uint64_t stringalloc_region_addr_tail,
        uint64_t stringptr_region_addr,
        uint32_t min_fieldno,
        uint32_t max_fieldno) {
          
    printf("TSIM driver Run\n");
    this->Init();
    this->Launch(
        descriptor_table_addr,
        cpp_obj_addr,
        has_bits_base_offset_only,
        stringalloc_region_addr_tail,
        stringptr_region_addr,
        min_fieldno,
        max_fieldno
    );
    this->WaitForCompletion(100000000);
    return 0;
  }

 private:
  void Init() {
    dpi_ = loader_->Get();
    dpi_->SimResume();
  }

  void Launch(
        uint64_t descriptor_table_addr,
        uint64_t cpp_obj_addr,
        uint64_t has_bits_base_offset_only,
        uint64_t stringalloc_region_addr_tail,
        uint64_t stringptr_region_addr,
        uint32_t min_fieldno,
        uint32_t max_fieldno
  ) {

    printf("TSIM driver Launch\n");
    printf("descriptor_table_addr %016lx\n", descriptor_table_addr);
    printf("cpp_obj_addr %016lx\n", cpp_obj_addr);
    printf("has_bits_base_offset_only %016lx\n", has_bits_base_offset_only);
    printf("stringalloc_region_addr_tail %016lx\n", stringalloc_region_addr_tail);
    printf("stringptr_region_addr %016lx\n", stringptr_region_addr);
    printf("min_fieldno %x\n", min_fieldno);
    printf("max_fieldno %x\n", max_fieldno);
    
    // 0x0 is the control
    // 0x4 not used
    // 0x8 first value
    // 0xc second value
    // 0x10 first ptr higher half
    // 0x14 first ptr lower half
    // 0x18 second ptr higher half
    // 0x1c second ptr lower half
    // 0x20 third ptr higher half
    // 0x24 third ptr lower half
    // 0x28 fourth ptr higher half
    // 0x2c fourth ptr lower half
    // 0x30 fifth ptr higher half
    // 0x34 fifth ptr lower half
  
    dpi_->WriteReg(0x8, min_fieldno);
    dpi_->WriteReg(0xc, max_fieldno);
    dpi_->WriteReg(0x10, descriptor_table_addr & 0xffffffff);
    dpi_->WriteReg(0x14, (descriptor_table_addr >> 32)& 0xffffffff);
    dpi_->WriteReg(0x18, cpp_obj_addr & 0xffffffff);
    dpi_->WriteReg(0x1c, (cpp_obj_addr >> 32) & 0xffffffff);
    dpi_->WriteReg(0x20, has_bits_base_offset_only & 0xffffffff);
    dpi_->WriteReg(0x24, (has_bits_base_offset_only >> 32) & 0xffffffff);
    dpi_->WriteReg(0x28, stringalloc_region_addr_tail & 0xffffffff);
    dpi_->WriteReg(0x2c, (stringalloc_region_addr_tail >> 32)& 0xffffffff);
    dpi_->WriteReg(0x30, stringptr_region_addr & 0xffffffff);
    dpi_->WriteReg(0x34, (stringptr_region_addr >> 32) & 0xffffffff);

    // start
    dpi_->WriteReg(0x00, 0x1);
  }

  void WaitForCompletion(uint32_t wait_cycles) {
    printf("TSIM driver WaitForCompletion\n");
    uint32_t i, val;
    for (i = 0; i < wait_cycles; i++) {
      val = dpi_->ReadReg(0x00);
      val &= 0x2;
      if (val == 0x2) break;  // finish
    }
    if (i == wait_cycles) {
      printf("TSIM driver timeout\n");
    }else{
      printf("TSIM driver done\n");
    }
    dpi_->SimWait();
  }

  // DPI loader
  DPILoader* loader_;
  // DPI Module
  DPIModuleNode* dpi_;
};


// TVM_REGISTER_GLOBAL("vta.tsim.init")
// .set_body([](TVMArgs args, TVMRetValue* rv) {
//     Module m = args[0];
//     DPILoader::Global()->Init(m);
//   });

}  // namespace tsim
}  // namespace vta

void vta_tsim_init() {
  protoacc::dpi::Module module = protoacc::dpi::DPIModuleNode::Load("/home/jiacma/protoacc/build/libvta_hw.so");
  protoacc::tsim::DPILoader::Global()->Init(module);
}

ProtoaccDeviceHandle ProtoaccDeviceAlloc() {
  return new protoacc::tsim::Device();
}

void ProtoaccDeviceFree(ProtoaccDeviceHandle handle) {
  delete static_cast<protoacc::tsim::Device*>(handle);
}

//  val descriptor_table_addr        = io.vcr.ptrs(0) << 32 | io.vrc.ptrs(1)
//   val cpp_obj_addr                 = io.vcr.ptrs(2) << 32 | io.vrc.ptrs(3) 
//   val has_bits_base_offset_only    = io.vcr.ptrs(4) << 32 | io.vcr.ptrs(5)
//   val stringalloc_region_addr_tail = io.vcr.ptrs(6) << 32 | io.vcr.ptrs(7)
//   val stringptr_region_addr        = io.vcr.ptrs(8) << 32 | io.vcr.ptrs(9)
//   val min_fieldno                  = io.vcr.vals(0)
//   val max_fieldno                  = io.vcr.vals(1)
int ProtoaccDeviceRun(
                ProtoaccDeviceHandle handle,
                uint64_t descriptor_table_addr,
                uint64_t cpp_obj_addr,
                uint64_t has_bits_base_offset_only,
                uint64_t stringalloc_region_addr_tail,
                uint64_t stringptr_region_addr,
                uint32_t min_fieldno,
                uint32_t max_fieldno
                 ) {
  return static_cast<protoacc::tsim::Device*>(handle)->Run(
      descriptor_table_addr,
      cpp_obj_addr,
      has_bits_base_offset_only,
      stringalloc_region_addr_tail,
      stringptr_region_addr,
      min_fieldno,
      max_fieldno);
}

void ProtoaccMemAlloc(int pid) {
  protoacc::vmem::VirtualMemoryManager::Global()->pid = pid;
  protoacc::vmem::VirtualMemoryManager::Global()->buffer = new uint8_t[1024];
}

void ProtoaccMemFree() {
  delete protoacc::vmem::VirtualMemoryManager::Global()->buffer;
}