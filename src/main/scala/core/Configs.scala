
package vta.core

import vta.util.config._

/** CoreConfig.
 *
 * This is one supported configuration for VTA. This file will
 * be eventually filled out with class configurations that can be
 * mixed/matched with Shell configurations for different backends.
 */
class CoreConfig extends Config((site, here, up) => {
  case CoreKey =>
    CoreParams(
      ProtoAccelPrintfEnable = true,
      batch = 1,
      blockOut = 16,
      blockOutFactor = 1,
      blockIn = 16,
      inpBits = 8,
      wgtBits = 8,
      uopBits = 32,
      accBits = 32,
      outBits = 8,
      uopMemDepth = 2048,
      inpMemDepth = 2048,
      wgtMemDepth = 1024,
      accMemDepth = 2048,
      outMemDepth = 2048,
      instQueueEntries = 512
    )
})