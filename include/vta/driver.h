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
 * \file vta/driver.h
 * \brief Driver interface that is used by runtime.
 *
 * Driver's implementation is device specific.
 */

#ifndef VTA_DRIVER_H_
#define VTA_DRIVER_H_

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>
#include <stdlib.h>

/*! \brief Memory management constants for cached memory */
#define VTA_CACHED 1
/*! \brief Memory management constants for non-cached memory */
#define VTA_NOT_CACHED 0

/*! \brief Physically contiguous buffer size limit */
#ifndef VTA_MAX_XFER
#define VTA_MAX_XFER (1<<25)
#endif

/*! PAGE SIZE */
#define VTA_PAGE_BITS 12
#define VTA_PAGE_BYTES (1 << VTA_PAGE_BITS)

/*! \brief Device resource context  */
typedef void * VTADeviceHandle;

/*! \brief physical address */
#ifdef USE_VTA64
typedef uint64_t vta_phy_addr_t;
#else
typedef uint32_t vta_phy_addr_t;
#endif

/*!
 * \brief Allocate a device resource handle
 * \return The device handle.
 */
VTADeviceHandle VTADeviceAlloc();

/*!
 * \brief Free a device handle
 * \param handle The device handle to be freed.
 */
void VTADeviceFree(VTADeviceHandle handle);

/*!
 * \brief Launch the instructions block until done.
 * \param device The device handle.
 * \param insn_phy_addr The physical address of instruction stream.
 * \param insn_count Instruction count.
 * \param wait_cycles The maximum of cycles to wait
 *
 * \return 0 if running is successful, 1 if timeout.
 */
int VTADeviceRun(VTADeviceHandle device,
                 vta_phy_addr_t insn_phy_addr,
                 uint32_t insn_count,
                 uint32_t wait_cycles);

void vta_tsim_init();

#ifdef __cplusplus
}
#endif
#endif  // VTA_DRIVER_H_
