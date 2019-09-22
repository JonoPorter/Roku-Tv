/**
 * Roku TV
 * Download: https://github.com/apwelsh/hubitat
 * Description:
 * This is a parent device handler designed to manage and control a Roku TV or Player connected to the same network 
 * as the Hubitat hub.  This device handler requires the installation of a child device handler available from
 * the github repo.
 *-------------------------------------------------------------------------------------------------------------------
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *-------------------------------------------------------------------------------------------------------------------
 **/
preferences {
    input name: "deviceIp",        type: "text",   title: "Device IP", required: true
    input name: "deviceMac",       type: "text",   title: "Device MAC Address", required: true, defaultValue: "UNKNOWN"
    input name: "refreshUnits",    type: "enum",   title: "Refresh interval measured in Minutes, or Seconds", options:["Minutes","Seconds"], defaultValue: "Minutes", required: true
    input name: "refreshInterval", type: "number", title: "Refresh the status at least every n ${refreshUnits}.  0 disables auto-refresh, which is not recommended.", range: 0..60, defaultValue: 5, required: true
    input name: "appRefresh",      type: "bool",   title: "Refresh current application status seperate from TV status.", defaultValue: false, required: true
    if (appRefresh) {
        input name: "appInterval",     type: "number", title: "Refresh the current application at least every n seconds.", range: 1..120, defaultValue: 60, required: true        
    }
    input name: "autoManage",      type: "bool",   title: "Create Child Devices for Apps Buttons ect?", defaultValue: false, required: true
    if (autoManage) {
        input name: "manageApps",      type: "bool",   title: "Enable management of Roku installed Applications", defaultValue: true, required: true
        input name: "manageAppNames",      type: "text",   title: "Name of apps you want as a button. Partial or full app names seperated by a ','", defaultValue: "", required: true
        input name: "hdmiPorts",       type: "enum",   title: "Number of HDMI inputs", options:["0","1","2","3","4"], defaultValue: "0", required: true
        input name: "inputAV",         type: "bool",   title: "Enable AV Input", defaultValue: false, required: true
        input name: "inputTuner",      type: "bool",   title: "Enable Tuner Input", defaultValue: false, required: true
        input name: "buttonsEnabled",      type: "bool",   title: "Add Devices for Play,Home,Back, ect", defaultValue: false, required: true
        if(buttonsEnabled){
            input name: "buttonPlay",      type: "bool",   title: "Add Device for the Play Button", defaultValue: true, required: true
            input name: "buttonHome",      type: "bool",   title: "Add Device for the Home Button", defaultValue: false, required: true
            input name: "buttonBack",      type: "bool",   title: "Add Device for the Back Button", defaultValue: false, required: true
            input name: "buttonFindRemote",      type: "bool",   title: "Add Device for the FindRemote Button", defaultValue: true, required: true
        }
    }
    input name: "logEnable",       type: "bool",   title: "Enable debug logging", defaultValue: true, required: true
}

metadata {
    definition (name:      "Roku TV", 
		namespace: "apwelsh", 
		author:    "Armand Welsh", 
		importUrl: "https://raw.githubusercontent.com/apwelsh/hubitat/master/devices/roku-tv.groovy") {
		
        capability "TV"
  //      capability "Music Player"
        capability "AudioVolume"
        capability "Switch"
        capability "Polling"
        capability "Refresh"

        command "home"
     /*   command "back"
        command "up"
        command "down"
        command "left"
        command "right"
        command "Play"
        command "fwd"*/
        
        
		command "keyPress", [[name:"Key Press Action", type: "ENUM", constraints: [
				"Home",      "Back",       "FindRemote",  "Select",        "Up",        "Down",       "Left",        "Right",
				"Play",      "Rev",        "Fwd",         "InstantReplay", "Info",      "Search",     "Backspace",   "Enter",
				"VolumeUp",	 "VolumeDown", "VolumeMute",  "Power",         "PowerOff",
				"ChannelUp", "ChannelDown", "InputTuner", "InputAV1",      "InputHDMI1", "InputHDMI2", "InputHDMI3", "InputHDMI4"] ] ]
        
        command "reloadApps"
		
        attribute "application", "string"
//		attribute "current_app_icon_html", "string"
    }
}

/**
 * Hubitat DTH Lifecycle Functions
 **/
def installed() {
    updated()
}

def updated() {
    if (logEnable) log.debug "Preferences updated"
	unschedule()
	if (deviceIp && refreshInterval > 0) {
        if (refreshUnits == "Seconds") {            
            schedule("${new Date().format("s")}/${refreshInterval} * * * * ?", refresh)
        } else {
    		schedule("${new Date().format("s")} 0/${refreshInterval} * * * ?", refresh)
        }
	}
    if (appRefresh && appInterval > 0) schedule("0/${appInterval} * * * * ?", queryCurrentApp)


}

/**
 * Builds a list of child devices
 **/
private def getChildrenInfo(Node body){
    def result = [];
    if (!autoManage) {
		return result
    }
    
    if (hdmiCount > 0 ) 
        (1..hdmiCount).each { i -> 
            result.add([netId :"hdmi${i}", appName:"HDMI ${i}",type:type_Input() ]) 
        }
    if (inputAV)  
        result.add( [netId :"AV1", appName:"AV",type:type_Input() ])  
    if (inputTuner)  
        result.add( [netId :"Tuner", appName:"Antenna TV",type:type_Input() ])  
    if(body) {
        String[] names = null;
        def shouldCheckNames = !!manageAppNames;
        if(shouldCheckNames){
            names = manageAppNames.split(",")
            shouldCheckNames = names && names.size() > 0 && names[0].size() > 0;
        }

        body.app.each{ node ->
                def appName = node.value()[0]; 
                if (node.attributes().type == "appl" 
                    && (!shouldCheckNames ||
                        names.any { name-> name.size()>0 && appName.matches("(?i:.*${name}.*)")  })
                    ) {
                 
                    result.add([netId : node.attributes().id, appName:appName, type:type_App() ])  
                }
            }
    }
    if(buttonsEnabled){
        if(buttonPlay)      { result.add( [netId :"Play", appName:"Play",type:type_Button() ])  }
        if(buttonHome)      { result.add( [netId :"Home", appName:"Home",type:type_Button() ])  }
        if(buttonBack)      { result.add( [netId :"Back", appName:"Back",type:type_Button() ])  }
        if(buttonFindRemote){ result.add( [netId :"FindRemote", appName:"Find Remote",type:type_Button() ])  }
    }
    return result
}



/**
 * Event Parsers
 **/
def parse(String description) {

}

def networkIdForApp(String appId) {
    return "${device.deviceNetworkId}-${appId}"    
}

def appIdForNetworkId(String netId) {
    return netId.replaceAll(~/.*\-/,"")
}

def iconPathForApp(String netId) {
	return "http://${deviceIp}:8060/query/icon/${appIdForNetworkId(netId)}"	
}

def type_App(){ "App"}
def type_Input(){ "Input"}
def type_Button(){ "Button"}



 
	
private def parseInstalledApps(Node body) {
 
    def childrenInfo = getChildrenInfo(body)
    state.childrenInfo = childrenInfo;
	childDevices.each{ child ->
        def childInfo = childrenInfo.find {x->networkIdForApp(x.netId) == child.deviceNetworkId }
        if (!childInfo) {
            if (logEnable) log.trace "Deleting child device: ${child.name} (${child.deviceNetworkId})"
            deleteChildDevice(child.deviceNetworkId)
        }
    }

    childrenInfo.each { item -> 
        
        updateChildApp(networkIdForApp(item.netId), item.appName) 
    }

}

private def purgeInstalledApps() {    
    if (manageApps) childDevices.each{ child ->
		deleteChildDevice(child.deviceNetworkId)
	}
}

private def parseActiveApp(Node body) {
    def app = body.app[0]?.value() 
    if (app != null) {
		def currentApp = "${app[0].replaceAll( ~ /\h/," ")}"  // Convert non-ascii spaces to spaces.
		sendEvent(name: "application", value: currentApp)

		childDevices.each { child ->
			def appName = "${child.name}"
			def value = (currentApp.equals(appName)) ? "on" : "off"
			child.sendEvent(name: "switch", value: value)
//			if (value == "on") 	sendEvent(name: "current_app_icon_html", value:"<img src=\"${iconPathForApp(child.deviceNetworkId)}\"/>")


        }
    }
}

private def parseState(Node body) {
    for (def node : body) {
        def key = node.name()
        if (key == null)
            continue
        
        if (isStateProperty(key)) {
            def value = node.value()
            if (value != null) {
                if (value[0] != this.state."${key}") {
                    this.state."${key}" = value[0]
                    if (logEnable) log.debug "set ${key} = ${value[0]}"
                }
            }
        }
    }
}

private def isStateProperty(String key) {
    switch (key) {
        case "serial-number":
        case "vendor-name":
        case "device-id":
        case "mode-name":
        case "screen-size":
        case "user-device-name":
        case "childrenInfo":
			return true
	}
	return false
}

private def cleanState() {
	def keys = this.state.keySet()
	for (def key : keys) {
		if (!isStateProperty(key)) {
			if (logEnable) log.debug("removing ${key}")
			this.state.remove(key)
		}
	}
}

private def parseMacAddress(Node body) {
	def type = body."network-type"[0]?.value()[0]
	def wifiMac = body."${type}-mac"[0]?.value()
    if (wifiMac != null) {
        def macAddress = wifiMac[0].replaceAll("[^A-f,a-f,0-9]","")
        if (!deviceMac || deviceMac != macAddress) {
            if (logEnable) log.debug "Update config [MAC Address = ${macAddress}]"
            device.updateSetting("deviceMac", [value: macAddress, type:"text"])

        }
    }
}

private def parsePowerState(Node body) {

    def powerMode = body."power-mode"[0]?.value()
    if (powerMode != null) {
        def mode = powerMode[0]

        switch (mode) {
            case "PowerOn":
                if (this.state!="on") {
                    sendEvent(name: "switch", value: "on")
                    if (appRefresh && appInterval > 0) {
                        runInMillis(100, queryCurrentApp)
                        schedule("0/${appInterval} * * * * ?", queryCurrentApp)
                    }
                } 
                break;
            case "PowerOff":
            case "DisplayOff":
            case "Headless":
                if (this.state!="off") {
                    sendEvent(name: "switch", value: "off")
                    unschedule(queryCurrentApp)
                }
                break;
        }
    }    
}

/*
 * Device Capability Interface Functions
 */

def on() {

  /*  sendHubCommand(new hubitat.device.HubAction (
        "wake on lan ${deviceMac}",
        hubitat.device.Protocol.LAN,
        null,
        [:]
    ))*/

    keyPress('Power')
}

def off() {
  //  sendEvent(name: "switch", value: "off")
    keyPress('PowerOff')
}

def home() {
    keyPress('Home')
}

def channelUp() {
    keyPress('ChannelUp')
}

def channelDown() {
    keyPress('ChannelDown')
}

def volumeUp() {
    keyPress('VolumeUp')
}

def volumeDown() {
    keyPress('VolumeDown')
}

def setVolume(level) {
    log.trace "set volume not supported by Roku"
}

def unmute() {
    keyPress('VolumeMute')
}

def mute() {
    keyPress('VolumeMute')
}

def poll() {
    if (logEnable)  log.trace "Executing 'poll'"
    if (appRefresh) runInMillis(100, queryCurrentApp)
    runInMillis(200, refresh)
}

def refresh() {
    if (logEnable) log.trace "Executing 'refresh'"
    runInMillis(500, queryDeviceState)
    if (!appRefresh) runInMillis(500, queryCurrentApp)
    runInMillis(500, queryInstalledApps)
}

/**
 * Custom DTH Command interface functions
 **/

def input_AV1() {
    keyPress('InputAV1')
}

def input_Tuner() {
    keyPress('InputTuner')
}

def input_hdmi1() {
    keyPress('InputHDMI1')
}

def input_hdmi2() {
    keyPress('InputHDMI2')
}

def input_hdmi3() {
    keyPress('InputHDMI3')
}

def input_hdmi4() {
    keyPress('InputHDMI4')
}

def reloadApps() {
    purgeInstalledApps()
    runIn(1,queryInstalledApps)
}


/**
 * Roku API Section
 * The following functions are used to communicate with the Roku RESTful API
 **/

def queryDeviceState() {
    asynchttpGet(
        deviceStateCallback,
        [  uri :"http://${deviceIp}:8060/query/device-info", ],
        null);
}
def deviceStateCallback(hubitat.scheduling.AsyncResponse response, java.util.LinkedHashMap data){
    def status = response.getStatus();
    if( 200 <= status && status < 300  ){
        def bodyText = response.getData();
        def body = new XmlParser().parseText(bodyText)
        cleanState()
        parseMacAddress body
        parsePowerState body
        parseState body
    }
    else{
         if (logEnable) log.debug "query/active-apps has failed ($status): ${response.getErrorMessage()}"
    }
}


def queryCurrentApp() {
    asynchttpGet(
        currentAppCallback,
        [  uri :"http://${deviceIp}:8060/query/active-app", ],
        null);
}
def currentAppCallback(hubitat.scheduling.AsyncResponse response, java.util.LinkedHashMap data){
    def status = response.getStatus();
    if( 200 <= status && status < 300  ){
        def bodyText = response.getData();
        def body = new XmlParser().parseText(bodyText)
        parseActiveApp(body)
    }
    else{
         if (logEnable) log.debug "query/active-apps has failed ($status): ${response.getErrorMessage()}"
    }
}

def queryInstalledApps() {
    asynchttpGet(
        appsCallback,
        [  uri :"http://${deviceIp}:8060/query/apps", ],
        null);
}
def appsCallback(hubitat.scheduling.AsyncResponse response, java.util.LinkedHashMap data){
    def status = response.getStatus();
    if( 200 <= status && status < 300  ){
        def bodyText = response.getData();
        def body = new XmlParser().parseText(bodyText)
        parseInstalledApps(body)
    }
    else{
         if (logEnable) log.debug "query/apps has failed ($status): ${response.getErrorMessage()}"
    }
}


def keyPress(key) {
	if (!isValidKey(key)) {
		log.warning("Invalid key press: ${key}")
		return
	}
    if (logEnable) log.debug "Executing '${key}'"
    asynchttpPost(
        keyPressCallback,
        [uri:"http://${deviceIp}:8060/keypress/${key}"],
        [key:key]);
}
def keyPressCallback(hubitat.scheduling.AsyncResponse response, java.util.LinkedHashMap data){
    def status = response.getStatus();
    if( 200 <= status && status < 300  ){
        if(data.key == "Power"){
            sendEvent(name: "switch", value: "on")
        }
        if(data.key == "PowerOff"){
            sendEvent(name: "switch", value: "off")
        }
    }
    else{
         if (logEnable) log.debug "keyPress(\"${data.key}\") has failed ($status): ${response.getErrorMessage()}"
    }
}

private def isValidKey(key) {
	def keys = [
		"Home",      "Back",       "FindRemote",  "Select",
		"Up",        "Down",       "Left",        "Right",
		"Play",      "Rev",        "Fwd",         "InstantReplay",
		"Info",      "Search",     "Backspace",   "Enter",
		"VolumeUp",	 "VolumeDown", "VolumeMute",
		"Power",     "PowerOff",
		"ChannelUp", "ChannelDown", "InputTuner", "InputAV1",
		"InputHDMI1", "InputHDMI2", "InputHDMI3", "InputHDMI4"
		]
	
	return keys.contains(key)
}

def getChildInfo(traceName,appId){
    if(!state.childrenInfo){
        if (logEnable) log.error "${traceName} ${appId}': No childrenInfo!"
        return null
    }
    def item = state.childrenInfo.find { it.netId == appId }
    if(item == null){
        if (logEnable) log.error "{traceName} ${appId}: No Child!"
        return null
    }
    return item;
}

Boolean childOn(appId) {
    def item = getChildInfo("childOn",appId);
    if(item == null){return false}
    if(item.type == type_App())
    {
        if (logEnable) log.debug "Executing 'launchApp ${appId}'"
        asynchttpPost(
            launchAppCallback,
            [uri:"http://${deviceIp}:8060/launch/${appId}"],
            [appId:appId]);
        return true;
    }
    else if(item.type == type_Input()){
        if (logEnable) log.debug "Executing 'input${appId}'"
        this."input_$appId"()
        return false;
    }
    else if(item.type==type_Button()){
        if (logEnable) log.debug "Executing 'keyPress ${appId}'"
        keyPress(appId)
        return false;
    }
    return false;
}
def childOff(appId){
    def item = getChildInfo("childOff",appId);
    if(item == null){return}
    if(item.type == type_App())
    {
        if (logEnable) log.debug "Executing 'home ${appId}'"
        this.home();
    }
}
def launchAppCallback(hubitat.scheduling.AsyncResponse response, java.util.LinkedHashMap data){
    def status = response.getStatus();
    if( 200 <= status && status < 300  ){
        refresh();
    }
    else{
         if (logEnable) log.debug "launch/${data.appId} has failed ($status): ${response.getErrorMessage()}"
    }
}

/**
 * Child Device Maintenance Section
 * These functions are used to manage the child devices bound to this device
 */

private def getChildDevice(String netId) {
    try {
        def result = null
        childDevices.each{ child ->
            if(child.deviceNetworkId == netId) {
                result = child
            }
        }
        return result
    } catch(e) {
        if (logEnable) log.error "Failed to find child with exception: ${e}";
    }
    return null
}

private void updateChildApp(String netId, String appName) {
    def child = getChildDevice(netId)
    if(child != null) { //If child exists, do not create it
        return
    }

    if (appName != null) {
        createChildApp(netId, appName)
    } else {
        if (logEnable) log.error "Cannot create child: (${netId}) due to missing 'appName'"
    }
}

private void createChildApp(String netId, String appName) {
    try {
        def label = deviceLabel()
        addChildDevice("Roku App", "${netId}",
            [label: "${label}-${appName}", 
             isComponent: false, name: "${appName}"])
        if (logEnable) log.debug "Created child device: ${appName} (${netId})"
    } catch(IllegalArgumentException e) {
        if (getChildDevice(netId)) {
            if (logEnabled) log.warn "Attempted to create duplicate child device for ${appName} (${netId}); Skipped"
        } else {
            if (logEnable) log.error "Failed to create child device with exception: ${e}"
        }
    } catch(Exception e) {
        if (logEnable) log.error "Failed to create child device with exception: ${e}"
    }
}

private def deviceLabel() {
    if (device.label == null)
        return device.name
    return device.label
}



