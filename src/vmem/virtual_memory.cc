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
 * \file virtual_memory.cc
 * \brief Thread-safe virtal memory manager
 */

#include "virtual_memory.h"

#include <unistd.h>
#include <vta/driver.h>
#include <cstdint>
#include <cstdlib>
#include <cstring>
#include <list>
#include <utility>
#include <iterator>
#include <unordered_map>
#include <map>
#include <mutex>
#include <fcntl.h>
#include <inttypes.h>


namespace protoacc {
namespace vmem {
  FILE *fp_read, *fp_write;
int fd =0 ;
int open_pid_mem(pid_t pid){
    char mem_path[64];
    snprintf(mem_path, sizeof(mem_path), "/proc/%d/mem", pid);
    int fd = open(mem_path, O_RDWR);
    if (fd == -1) {
        perror("open");
        return -1;
    }
    fp_read = fopen("/tmp/mem_read_log.txt", "w");
    fp_write = fopen("/tmp/mem_write_log.txt", "w");
    return fd;
}

int close_pid_mem(int fd){
    if (close(fd) == -1) {
        perror("close");
        return -1;
    }
    fclose(fp_read);
    fclose(fp_write);
    return 0;
}

ssize_t vm_read_process_memory(int fd, uintptr_t address, void *buffer, size_t size) {
   //log out to a file 
    // fprintf(fp_read, "%" PRIxPTR " %zu\n", address, size);
    printf("VirtualMemoryManager::Read, addr %p, size %ld\n", (void*)address, size);
    ssize_t nread = pread(fd, buffer, size, address);
    for(int i = 0; i < nread; i++) {
        printf("%02x", ((unsigned char*)buffer)[i]);
    }
    printf("\n");
    return nread;
}

ssize_t vm_write_process_memory(int fd, uintptr_t address, void *buffer, size_t size){
    fprintf(fp_write, "%" PRIxPTR " %zu\n", address, size);
    ssize_t nwrite = pwrite(fd, buffer, size, address);
    return nwrite;
}

/*!
 * \brief Get virtual address given physical address.
 * \param phy_addr The simulator phyiscal address.
 * \return The true virtual address;
 */
void* VirtualMemoryManager::GetAddr(uint64_t phy_addr, int size_t) {
  if(fd == 0){
    fd = open_pid_mem(pid);
  }
  // avoid clash
  void *buffer = malloc(size_t*8);
  vm_read_process_memory(fd, phy_addr, buffer, size_t*8);
  return buffer;
}

VirtualMemoryManager* VirtualMemoryManager::Global() {
  static VirtualMemoryManager inst;
  return &inst;
}

}  // namespace vmem
}  // namespace vta