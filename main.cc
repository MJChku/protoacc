
#include <vta/driver.h>

int main(){
    vta_tsim_init();
    VTADeviceHandle device = VTADeviceAlloc();
    VTADeviceFree(device);
    return 0;
}