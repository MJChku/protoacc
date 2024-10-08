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

/*!
 * \file virtual_memory.h
 * \brief The virtual memory manager for device simulation
 */

#ifndef VTA_VMEM_VIRTUAL_MEMORY_H_
#define VTA_VMEM_VIRTUAL_MEMORY_H_

#include <vta/driver.h>
#include <cstdint>
#include <type_traits>
#include <mutex>
#include <vector>
#include <map>
#include <unordered_map>
#include <memory>

enum VMemCopyType {
  kVirtualMemCopyFromHost = 0,
  kVirtualMemCopyToHost = 1
};

namespace protoacc {
namespace vmem {

/*!
 * \brief DRAM memory manager
 *  Implements simple paging to allow physical address translation.
 */
class VirtualMemoryManager {
 public:
  /*!
   * \brief Get virtual address given physical address.
   * \param phy_addr The simulator phyiscal address.
   * \return The true virtual address;
   */
  void* GetAddr(uint64_t phy_addr, int size_t);
 
  static VirtualMemoryManager* Global();

    int pid;
    uint8_t* buffer;
};

}  // namespace vmem
}  // namespace vta

#endif  // VTA_VMEM_VIRTUAL_MEMORY_H_