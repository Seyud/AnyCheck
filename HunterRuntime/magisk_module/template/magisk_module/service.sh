#set_property_if_exist() {
#    local property=$1
#    local value=$2
#    setprop "$property" "$value"
#}
#
#remove_env_var() {
#    local var_name=$1
#    resetprop --delete "$var_name"
#}
#
##remove camera sensor
#remove_env_var "vendor.camera.sensor.frontMain.fuseID"
#remove_env_var "vendor.camera.sensor.rearMain.fuseID"
#remove_env_var "vendor.camera.sensor.rearTele.fuseID"
#remove_env_var "vendor.camera.sensor.rearTele4x.fuseID"
#remove_env_var "vendor.camera.sensor.rearUltra.fuseID"
#
#remove_env_var "ro.ril.oem.imei"
#remove_env_var "ro.ril.oem.imei1"
#remove_env_var "ro.ril.oem.imei2"
#remove_env_var "ro.ril.oem.meid"
#remove_env_var "ro.ril.oem.psno"
#remove_env_var "ro.ril.oem.sno"
#
#remove_env_var "ro.vendor.oem.imei"
#remove_env_var "ro.vendor.oem.imei1"
#remove_env_var "ro.vendor.oem.imei2"
#remove_env_var "ro.vendor.oem.meid"
#remove_env_var "ro.vendor.oem.psno"
#remove_env_var "ro.vendor.oem.sno"
#
#
## init runtime base prop
#set_property_if_exist "sys.usb.config" "none"
#set_property_if_exist "sys.usb.state" "none"
#set_property_if_exist "persist.sys.usb.config" "none"
#set_property_if_exist "persist.sys.usb.qmmi.func" "none"
#set_property_if_exist "vendor.usb.mimode" "none"
#set_property_if_exist "persist.vendor.usb.config" "none"
#set_property_if_exist "ro.debuggable" "0"
#set_property_if_exist "init.svc.adbd" "stopped"
#set_property_if_exist "ro.secure" "1"
#set_property_if_exist "ro.boot.flash.locked" "1"
#set_property_if_exist "sys.oem_unlock_allowed" "0"
#set_property_if_exist "ro.boot.verifiedbootstate" "green"
