// ******* PARAMETERS PARSED... ********
// Model - name of microscope model
// Objective - name of objective
// ScanMode - scan mode
// DwellTime - dwell time (usec)
// Voxel - voxel size (um x um x um)
// --- Channel Specific ---
// LaserPower - laser power (%)
// Excitation - ex wavelength (nm)
// Detection - detection bandpass (nm-nm)
// Gain - detector gain (hv or au)
// Pinhole - pinhole size (AU)

// The key strings as well as value formatting are vendor-specific, system specific, channel number specific, and channel type specific!!!

// #1.  Find vendor format
fname = getTitle();
name = split(fname, ".");
ext = name[1];

// #2.  Parse info based on vendor and model
if (ext == "nd2") {
	
	// Parse first based on model...
	model = Property.get("LightPathName");
	if (model=="Nikon AX") { image_metadata = NikonAX(); }
	else if (model=="Nikon A1") { image_metadata = NikonA1(); }
	else {print("Error: "+model+" is an unsupported system");}
}	
else if (ext == "czi") {

	// Parse first based on model...
	model = substring(Property.get("Information|Instrument|Microscope|System"), 0, 7); // trims stand name
	if (model=="LSM 780") { image_metadata = ZeissLSM780(); }
	else if (model=="LSM 800") { image_metadata = ZeissLSM800(); }  // 980 stills uses 800
	else {print("Error: "+model+" is an unsupported system");}
}	
else {
	print("Error: Only Nikon (nd2) and Zeiss (czi) are currently supported"); // Show the basics that you can get...
}

// --- Output ----

Array.show(image_metadata);  // TESTING


// ----------------------- Parsing Functions ------------------------

// --- Nikon A1 ---
function NikonA1() { 
	
	// Global parsing...	
	model = Property.get("LightPathName");
	objective = Property.get("sObjective");
	scanMode = Property.get("{Channel Series Mode}");
	dwellTime = Property.get("{Scan Speed}");
	getVoxelSize(x, y, z, unit);
	
	// Values Formatting
	model = model;
	objective = objective;
	scanMode = scanMode;
	dwellTime = ""+dwellTime+" usec";
	voxel = ""+x+" x "+y+" x "+z+" "+unit;
	
	globals = newArray(model, objective, scanMode, dwellTime, voxel);
	nikonA1props = globals;

	// Channel-specific parsing...
	numchs = Property.get("SizeC");
	channels = newArray();
	ords = newArray("First", "Second", "Third", "Fourth", "Fifth"); // filters are described using ordinals.  Should match length of hc array.
	
	// Metadata channel numbers are hardware-defined and independent of channel numbering in the image
	// Finds which hardware channels were used...
	if (Property.get("CH1 {Laser Wavelength} #1") == "") {ch1=0;} else {ch1=1;}  // the trailing #d is constant
	if (Property.get("CH2 {Laser Wavelength} #1") == "") {ch2=0;} else {ch2=2;} 
	if (Property.get("CH3 {Laser Wavelength} #1") == "") {ch3=0;} else {ch3=3;} 
	if (Property.get("CH4 {Laser Wavelength} #1") == "") {ch4=0;} else {ch4=4;} 
	if (Property.get("CH5 {Laser Wavelength} #1") == "") {ch5=0;} else {ch5=5;} 
	hc = newArray(ch1,ch2,ch3,ch4,ch5); // hardware defined channels
	
	for (i = 0; i < hc.length; i++) { // indexes through hardware channels to find which were used
		
		if (hc[i] > 0) {
		
		laserPower = Property.get("CH"+hc[i]+"LaserPower");
		excitation =  Property.get("CH"+hc[i]+" {Laser Wavelength} #1");  // the #d is constant
		detection = Property.get("{"+ords[hc[i]-1]+" Filter Cube}");
		gain = Property.get("CH"+hc[i]+"PMTHighVoltage");
		pinhole = Property.get("{Pinhole Size(um)} #"+hc[i]);		

		// Value Formattting
		laserPower = ""+laserPower+" %";
		excitation = substring(excitation, 0, 3)+" nm";  // trims nested key
		// Detecton bandpass
			center = round(substring(detection, 0, 3));
			radius = round(substring(detection, 4, 6)/2);
			fstart = center-radius;
			fend = center+radius;
		detection = ""+fstart+" - "+fend+" nm";
		gain = gain+" au";
		pinhole = pinhole+" um";

		channels = newArray(laserPower, excitation, detection, gain, pinhole);

		nikonA1props = Array.concat(nikonA1props, channels);
		
		}
		
	}

	return nikonA1props;  // the properties as an array
	
}

// --- Nikon AX ---
function NikonAX() { 
	
	// Global parsing...	
	model = Property.get("LightPathName");
	objective = Property.get("sObjective");
	scanMode = Property.get("Series Mode");  // KEY NOT ALWAYS PRESENT...May imply simultaneous scanning?
	dwellTime = Property.get("Dwell Time");
	getVoxelSize(x, y, z, unit);
	
	// Values formatting
	voxel = ""+x+" x "+y+" x "+z+" "+unit;
	
	globals = newArray(model, objective, scanMode, dwellTime, voxel);
	nikonAXprops = globals;
	
	
	// Channel-specific parsing...
	numchs = Property.get("SizeC");
	channels = newArray();
	
	// Check which channels are confocal vs transmitted.
	for (c = 1; c <= numchs; c++) {
		if (numchs == 1) {modality = Property.get("Modality");} else {modality = Property.get("Modality #"+c);}
		if (substring(modality, 0, 11) == "Brightfield") {tlc = c;} else {tlc = 0;};
	}
	
	for (c = 1; c <= numchs; c++) {
		
		if (c != tlc) { // skips transmitted channels
			
			// laserPower
			if (numchs == 1) {laserPower = Property.get("Power");} else {laserPower = Property.get("Power #"+c);}
			excitation = "TBD";  // DUE TO AN ERROR IN KEY-VALUE PAIRING, only way to get laser lines is to search info line by line for "Laser NNN nm" where NNN is the wavelength.  If search returns true, then that laser was used.
			detection = Property.get("Emission Range #"+c);
			gain = Property.get("Gain #"+c);
			pinhole = Property.get("Pinhole Size #"+c);		
	
			// Value Formattting
			laserPower = laserPower+" %";
			gain = gain+" au";
	
			channels = newArray(laserPower, excitation, detection, gain, pinhole);
	
			nikonAXprops = Array.concat(nikonAXprops, channels);
		}
		
	}

	return nikonAXprops;  // the properties as an array
	
}



// ----- Zeiss LSM 780 Parsing -----
function ZeissLSM780() {
	
	numchs = Property.get("SizeC");
	
	// Global parsing...	
	model = substring(Property.get("Information|Instrument|Microscope|System"), 0, 7); // trims stand name
	objective = Property.get("Information|Instrument|Objective|Manufacturer|Model");
	// Scan Mode
	if (numchs == 1) {scanMode = Property.get("Information|Image|Channel|LaserScanInfo|ScanningMode");} else {scanMode = Property.get("Information|Image|Channel|LaserScanInfo|ScanningMode #1");} // shown for each channel when multiple channels present
	// Dwell time
	if (numchs ==1) {dwellTime = Property.get("Information|Image|Channel|LaserScanInfo|PixelTime");} else {dwellTime = Property.get("Information|Image|Channel|LaserScanInfo|PixelTime #1");}  // shown for each channel when multiple channels present
	getVoxelSize(x, y, z, unit);
	
	// Value Formatting
	model = substring(model, 0, 7); // trims possible stand name
	objective = objective;
	scanMode = scanMode;
	dwellTime = substring(dwellTime, 0, 4)+" usecs";
	voxel = ""+x+" x "+y+" x "+z+" "+unit;
	
	globals = newArray(model, objective, scanMode, dwellTime, voxel);
	zeissLSM780props = globals;
	
	
	// Channel-specific parsing...
	numchs = Property.get("SizeC");
	channels = newArray();
	fluochs = newArray(); // holds the channel index of each fluorescence channel
	i=0;
	
	// Check which channels are confocal vs transmitted.
	for (c = 1; c <= numchs; c++) {
		if (numchs == 1) {modality = Property.get("Information|Image|Channel|ContrastMethod");} else {modality = Property.get("Information|Image|Channel|ContrastMethod #"+c);}
		if (modality != "Other") {fluochs[i] = c; i++;} // 'Other' indicates transmitted. Records the index of each fluorescent channel.
	}
	// Number of fluorescence channels
	numchs = fluochs.length;
	
	for (c = 1; c <= numchs; c++) {
		
			// Keys depend on number of channels used
			if (numchs == 1) {  
				laserPower = Property.get("Experiment|AcquisitionBlock|Laser|LaserPower");
				excitation =  Property.get("Information|Image|Channel|Wavelength");
				detection = Property.get("Information|Image|Channel|DetectionWavelength|Ranges");
				gain = Property.get("Information|Image|Channel|Gain");
				pinhole = Property.get("Information|Image|Channel|VirtualPinholeSize");	// not certain this is correct field
		
				// Value Formattting
				laserPower = ""+parseFloat(laserPower)+" %";  /// 2P might have different units than vis lines
				excitation = excitation+" nm";
				detection = detection+" nm";
				gain = ""+round(parseFloat(gain))+" volts"; // volts
				pinhole = pinhole+" AiryUnits"; 
		
				channels = newArray(laserPower, excitation, detection, gain, pinhole);
		
				zeissLSM780props = Array.concat(zeissLSM780props, channels);
				
			} else if (numchs > 1) {  // index based on elements of fluochs, which correspond to the fluorescence chs numbers per se

				// indexing according to number of fluorescence channels only
				laserPower = Property.get("Information|Instrument|LightSource|Power #"+c);
				detection = Property.get("Information|Image|Channel|DetectionWavelength|Ranges #"+c);
				pinhole = Property.get("Information|Image|Channel|PinholeSizeAiry #"+c);
				// indexing according to all channels, with only the fluorescence index numbers being used here
				excitation =  Property.get("Information|Image|Channel|Wavelength #"+fluochs[c-1]);
				gain = Property.get("Information|Image|Channel|Gain #"+fluochs[c-1]);
					
					
				// Value Formattting
				laserPower = ""+parseFloat(laserPower)/10+" %";
				excitation = excitation+" nm";
				detection = detection+" nm";
				gain = ""+round(parseFloat(gain))+" volts"; // volts
				pinhole = substring(pinhole, 0, 4)+" AiryUnits"; 
		
				channels = newArray(laserPower, excitation, detection, gain, pinhole);
		
				zeissLSM780props = Array.concat(zeissLSM780props, channels);
				
			}

		
	}

	return zeissLSM780props;  // the properties as an array
	

}

// ----- Zeiss LSM 980 Parsing -----
function ZeissLSM800() {
	
	// Global parsing...	
	model = Property.get("Information|Instrument|Microscope|System"); 
	objective = Property.get("Information|Instrument|Objective|Manufacturer|Model");
	scanMode = Property.get("Information|Image|Channel|LaserScanInfo|ScanningMode #1"); // shown for each channel
	dwellTime = Property.get("Information|Image|Channel|LaserScanInfo|PixelTime #1"); 
	getVoxelSize(x, y, z, unit);
	
	// Value Formatting
	model = substring(model, 0, 7); // trims possible stand name
	objective = objective;
	scanMode = scanMode;
	dwellTime = substring(dwellTime, 0, 4)+" usec";
	voxel = ""+x+" x "+y+" x "+z+" "+unit;
	
	globals = newArray(model, objective, scanMode, dwellTime, voxel);
	zeissLSM800props = globals;
	
	
	// Channel-specific parsing...
	numchs = Property.get("SizeC");
	channels = newArray();
	fluochs = newArray(); // holds the channel index of each fluorescence channel
	i=0;
	
	// Check which channels are confocal vs transmitted.
	for (c = 1; c <= numchs; c++) {
		if (numchs == 1) {modality = Property.get("Information|Image|Channel|ContrastMethod");} else {modality = Property.get("Information|Image|Channel|ContrastMethod #"+c);}
		if (modality != "Other") {fluochs[i] = c; i++;} // 'Other' indicates transmitted. Records the index of each fluorescent channel.
	}
	// Number of fluorescence channels
	numchs = fluochs.length;
	
	for (c = 1; c <= numchs; c++) {
		
		// Keys depend on number of channels used
		if (numchs == 1) {  
		
// *** TODO - Have not confirmed single channel behavior on 980		
			laserPower = Property.get("Information|Instrument|LightSource|Power");
			excitation =  Property.get("Information|Instrument|LightSource|Id");
			detection = Property.get("Information|Image|Channel|DetectionWavelength|Ranges");
			gain = Property.get("Information|Image|Channel|Voltage");
			pinhole = Property.get("Information|Image|Channel|PinholeSizeAiry");		
	
			// Value Formattting
			laserPower = ""+parseFloat(laserPower)/10+" %";
			excitation = substring(excitation, 18, 21)+" nm";  // last 3 digits are the wavelength
			detection = detection+" nm";
			gain = ""+round(parseFloat(gain))+" volts"; // volts
			pinhole = substring(pinhole, 0, 4)+" AiryUnits"; 
	
			channels = newArray(laserPower, excitation, detection, gain, pinhole);
		
		} else if (numchs > 1) {  // index based on elements of fluochs, which correspond to the fluorescence chs numbers per se

			// indexing according to number of fluorescence channels only
			laserPower = Property.get("Information|Instrument|LightSource|Power #"+c);
			pinhole = Property.get("Information|Image|Channel|PinholeSizeAiry #"+c);
			excitation =  Property.get("Information|Instrument|LightSource|Id #"+c); // a long string
			// indexing according to all channels, with only the fluorescence index numbers being used here			
				//Alternatively: excitation =  Property.get("Information|Image|Channel|Wavelength #"+fluochs[c-1]), but this was less general
			detection = Property.get("Information|Image|Channel|DetectionWavelength|Ranges #"+fluochs[c-1]);
			gain = Property.get("Information|Image|Channel|Voltage #"+fluochs[c-1]);
		
			// Value Formattting
			laserPower = ""+parseFloat(laserPower)/10+" %";
			excitation = substring(excitation, 18, 21)+" nm";  // last 3 digits are the wavelength
			detection = detection+" nm";
			gain = ""+round(parseFloat(gain))+" volts"; // volts
			pinhole = substring(pinhole, 0, 4)+" AiryUnits"; 
	
			channels = newArray(laserPower, excitation, detection, gain, pinhole);
	
			zeissLSM800props = Array.concat(zeissLSM800props, channels);

		}
		
	}

	return zeissLSM800props;  // the properties as an array

}