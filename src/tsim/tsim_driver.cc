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

#include <vta/driver.h>
#include <vta/dpi/module.h>


namespace vta {
namespace tsim {

using vta::dpi::DPIModuleNode;
using vta::dpi::Module;

class DPILoader {
 public:
  ~DPILoader() {
    dpi_->SimResume();
    dpi_->SimFinish();
  }

  void Init(Module module) {
    mod_ = module;
    dpi_ = this->Get();
    dpi_->SimLaunch();
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

  int Run(vta_phy_addr_t insn_phy_addr,
          uint32_t insn_count,
          uint32_t wait_cycles) {
    this->Init();
    this->Launch(insn_phy_addr,
                 insn_count,
                 wait_cycles);
    this->WaitForCompletion(wait_cycles);
    return 0;
  }

 private:
  void Init() {
    dpi_ = loader_->Get();
    dpi_->SimResume();
  }

  void Launch(vta_phy_addr_t insn_phy_addr,
              uint32_t insn_count,
              uint32_t wait_cycles) {
    dpi_->WriteReg(0x08, insn_count);
    dpi_->WriteReg(0x0c, insn_phy_addr);
    dpi_->WriteReg(0x10, 0);
    dpi_->WriteReg(0x14, 0);
    dpi_->WriteReg(0x18, 0);
    dpi_->WriteReg(0x1c, 0);
    dpi_->WriteReg(0x20, 0);
    // start
    dpi_->WriteReg(0x00, 0x1);
  }

  void WaitForCompletion(uint32_t wait_cycles) {
    uint32_t i, val;
    for (i = 0; i < wait_cycles; i++) {
      val = dpi_->ReadReg(0x00);
      val &= 0x2;
      if (val == 0x2) break;  // finish
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
  vta::dpi::Module module = vta::dpi::DPIModuleNode::Load("/home/jiacma/protoacc/build/libvta_hw.so");
  vta::tsim::DPILoader::Global()->Init(module);
}

VTADeviceHandle VTADeviceAlloc() {
  return new vta::tsim::Device();
}

void VTADeviceFree(VTADeviceHandle handle) {
  delete static_cast<vta::tsim::Device*>(handle);
}

int VTADeviceRun(VTADeviceHandle handle,
                 vta_phy_addr_t insn_phy_addr,
                 uint32_t insn_count,
                 uint32_t wait_cycles) {
  return static_cast<vta::tsim::Device*>(handle)->Run(
      insn_phy_addr,
      insn_count,
      wait_cycles);
}
