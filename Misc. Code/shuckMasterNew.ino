#include <Wire.h>
#include <RTClib.h>
#include <EEPROM.h>
#include "src/FileLogger.h"
#include "src/util/PacketReceiver.h"
#include "src/util/PacketSender.h"
#include "src/Packets.h"
#include "src/Durafet.h"
#include <Ezo_i2c.h>
#include "src/TimedToggleRelay.h"
#include "SD.h"


Ezo_board EC = Ezo_board(100, "EC");      //create an EC circuit object who's address is 100 and name is "EC"
RTC_DS3231 rtc;  // Instantiate Real-Time Clock as rtc
FileLogger logger;
char dataLabel[16] = "PH";

HardwareSerial& hm10 = Serial2;

const uint8_t sensorID = 4;
const uint32_t syncPattern = 0x04030201;
const uint32_t eepromMagicAddress = 0x100;  //Magic value and address used to see if a shuck master has been configured before
const uint32_t eepromMagicValue = 0xCAFEF00D;

char receiveBuf[256];
PacketReceiver receiver(receiveBuf, syncPattern);  // create Receiver buffer to send data

char sendBuf[256];
PacketSender sender(sendBuf, syncPattern);  // create Reciever buffer to send data

// Initialize all actions to false by default
bool sending = false;
bool sdHealthy = false;
bool rtcHealthy = false;
//bool dfHealthy = false;

unsigned long lastByteReadMillis = 0;
unsigned long lastPHStoredMillis = 0;

struct Config cachedConfig;

//Setting variables for the Pico pH
const char *picoDelimiter =" ";
char* command;
char* channel;
char* s_value;
char* R0; //command status
char* R1; //reserved 
char* R2; //reserved 
char* R3; //reserved 
char* R4; //reserved 
char* R5; //tempSample
char* R6; //tempCase
char* R7; //signalIntensity
char* R8; //ambientLight
char* R9; //pressure
char* R10; //humidity
char* R11; //resisorTemp
char* R12; //reserved 
char* R13; //reserved 
char* R14; //pH
//float pH;
//float temperature;

//Setting variables for the salinity sensor
char* data;
String data_string;
char* token;
const char *delimiter =","; //Used to parse through the data
char* con;
char* tds;
char* sal;
char* gravity;
float salinity;
float conductivity;

//Initializing constants for the SD card (only used for creating the csv file)
const int CSpin = 53; //SD card chip select is set to pin 53
File sensorData;
String dataString = "";
String fileName = "";
// Do we need to initialize a date and time object for the clock?

void setup() {
  Serial.begin(9600);  // Set Baud Rate to 9600
  Serial1.begin(19200); //For the Pico pH
  Serial2.begin(9600);
  Wire.begin();

  //Checks for the SD card and the RTC
  if (logger.Init(CSpin)) {
    Serial.println("SD card initialized successfully");
    sdHealthy = true;
  } else {
    Serial.println("Failed to initialize SD card! uh oh.");
    
  }
  
  if (rtc.begin()) {
    Serial.println("RTC initialized successfully");
    rtcHealthy = true;
  } else {
    Serial.println("Couldn't find RTC");
    // while (1) delay();
  }
  //

  lastByteReadMillis = millis();
  lastPHStoredMillis = millis();

  // check eeprom for magic value; if not written, assume first run and initialize persistent config
  // this sets the value, ph period, and the eeprommagicvalue/address
  uint32_t eepromMagic = 0;
  EEPROM.get(eepromMagicAddress, eepromMagic);
  //if (eepromMagic != eepromMagicValue) {
  if (true) {
    Serial.println("EEPROM magic value not found, initializing configuration");
    cachedConfig;
    cachedConfig.unixtime = rtc.now().unixtime();
    cachedConfig.phPeriod = 1; // 1 second
    EEPROM.put(0, cachedConfig);
    EEPROM.put(eepromMagicAddress, eepromMagicValue);
  } else {
    EEPROM.get(0, cachedConfig);
  }

  Serial.println("Config stored:");
  Serial.print("Last set time: ");
  Serial.println(cachedConfig.unixtime);
  Serial.print("PH measurement period: ");
  Serial.println(cachedConfig.phPeriod);
  Serial.print("Standard pH: ");
  Serial.println(cachedConfig.stdPh, 4);
  Serial.print("Standard Temperature: ");
  Serial.println(cachedConfig.stdTemperature, 4);
  Serial.print("Standard Voltage: ");
  Serial.println(cachedConfig.stdVoltage, 4);

  // update calibration with stored values
  EC.send_cmd_with_num("K,",1); //setting the k value to 1
  receiver.Begin();
  sending = false;

}

void printDate(const DateTime& date) {
  Serial.print(date.year(), DEC);
  Serial.print("/");
  Serial.print(date.month(), DEC);
  Serial.print("/");
  Serial.print(date.day(), DEC);
  Serial.print(" ");
  Serial.print(date.hour(), DEC);
  Serial.print(':');
  Serial.print(date.minute(), DEC);
  Serial.print(':');
  Serial.print(date.second(), DEC);
}


void loop() {

  if (rtcHealthy && sdHealthy && millis() - lastPHStoredMillis > cachedConfig.phPeriod * 1000) {
    
    //Checks if the SD card has a file for the current day. If not, then it will create the file with the labels for each data
    DateTime name = rtc.now();
    fileName = String(name.month()) + "_" + String(name.day()) + "_" + String(name.year()-2000) + ".csv"; //format should look like month_day_year
    Serial.println(fileName);
    if (!SD.exists(fileName)){
      Serial.println("card initialized.");
      Serial.println("Now creating file...");
      sensorData = SD.open(fileName, FILE_WRITE);

      if(sensorData){
        Serial.println("File is open... now creating titles");
        String titles = String("time") + "," + String("pH") + "," + String("temperature") + "," + String("salinity") + "," + String("conductivity"); // convert to CSV
        sensorData.println(titles);
        Serial.println("Done creating titles");
        sensorData.close(); // close the file
      }
      else{
        Serial.println("File failed to be created/opened");
      }
    }

    /*
    Serial1.print("MEA 1 47")
    delay(1000); //Adding a 1 second delay
    String received_string = "";
    while(Serial1.available() > 0) {
      char received_char = Serial.read();
      received_string += received_char; //need to decompose this string
    }
    ph = ;
    temp = ;
    */    
    //float ph = 0.0;
    //float temp = 25.0;
    ezoRead(temp);
    Serial.print("Made pH, temperature, salinity, and conductivity measurements! pH: ");
    Serial.print(ph, 4);
    Serial.print(",\tTemperature: ");
    Serial.print(temp, 4);
    Serial.println(" C");
    Serial.print(",\tSalinity: ");
    Serial.print(salinity, 4);
    Serial.println(" PSU(ppt)");
    Serial.print(",\tConductivity: ");
    Serial.print(conductivity, 4);
    Serial.println(" uS");

    // log ph
    //Serial.println("Testing logging");
    logger.LogFloat(rtc.now(), "pH", ph);

    // log temperature
    logger.LogFloat(rtc.now(), "tp", temp);

    // log salinity
    logger.LogFloat(rtc.now(), "sa", salinity);

    // log conductivity
    logger.LogFloat(rtc.now(), "co", conductivity);

    lastPHStoredMillis = millis();
    //Serial.println("finished logging");

    //if sensorData file is open, then log of all the measured data in the AllData.csv file
    sensorData = SD.open(fileName, FILE_WRITE);
    DateTime time = rtc.now();
    String timeStamp = String(time.month()) + "/" + String(time.day()) + "/" + String(time.year()) + "  " + String(time.hour()) +":"+ String(time.minute());
    dataString = String(timeStamp) + "," + String(ph) + "," + String(temp)+ "," + String(salinity)+ "," + String(conductivity); // convert to CSV
    Serial.print("Now saving the string: ");
    Serial.println(dataString);
    sensorData.println(dataString);
    sensorData.close(); // close the file
  }
  
  if (sending) {
    Serial.println("Testing sending");
    const int N = 10; // number of entries per packet, TODO put this somewhere else
    struct FileEntry entries[N];
    
    // start building packet
    sender.Begin(PACKET_DATA);

    // send number of entries
    // sender.AddByte((byte) (N & 0xFF));
    
    // grab up to N entries from logger (or until entries run out)
    //  - add entry to packet
    Serial.println("-----------");

    byte numEntries = 0;    
    for (int i = 0; i < N && logger.Available(); i++) {
      struct FileEntry entry;
      DateTime dt;
      logger.ReadEntry(&(entries[i]));
      // sender.AddBuf((char *) &entry, sizeof(entry));
      dt = DateTime(entries[i].unixTime);
      printDate(dt);
      Serial.print("\t");
      Serial.println(entries[i].floatVal);
      numEntries++;
    }
    
    // add sensor id
    sender.AddByte(sensorID);
    
    // send number of entries
    sender.AddByte((byte) (numEntries & 0xFF));

    for (int i = 0; i < numEntries; i++) {
      sender.AddBuf((char *) &(entries[i]), sizeof(struct FileEntry));
    }

    // add the label to the end of the packet
    logger.GetLabel(dataLabel);
    Serial.print("Appending this label to the end of the packet: ");
    Serial.println(dataLabel);
    sender.AddStr(dataLabel);
    
    // send packet
    sender.Send(hm10);

    // if no more entries, close logger
    if (!logger.Available()) {
      Serial.println("Closing...");
      logger.Close();
      sending = false;
    }
  }
  
  if (hm10.available()) {
    //Serial.println("Testing hm10av");
    char readByte = hm10.read();
    lastByteReadMillis = millis();    // save time we read a byte at
    if (receiver.AddByte(readByte)) {
      receiver.PrintPacketInfo();
      switch((PacketType)receiver.GetPacketID()) {
        case PACKET_PING: {
          Serial.println("Received Ping packet");
          // send a pong back
          sender.Begin(PACKET_PING);
          sender.Send(hm10);
          break;
        }
        case PACKET_HEALTH: {
          Serial.println("Received Health packet");
          struct HealthPacket healthPacket;
          healthPacket.healthField = 0;
          if (rtc.lostPower()) {
            // lost power since we last checked, unhealthy
            rtcHealthy = false;
          }
          if (rtcHealthy) {
            Serial.println("RTC Healthy!");
            healthPacket.healthField |= 1 << HEALTH_RTC;
          } else {
            Serial.println("RTC Unhealthy!");
          }
          if (sdHealthy) {
            Serial.println("SD Healthy!");
            healthPacket.healthField |= 1 << HEALTH_SD;
          } else {
            Serial.println("SD Unhealthy!");
          }
          sender.Begin(PACKET_HEALTH);
          sender.AddBuf((char *) &healthPacket, sizeof(healthPacket));
          sender.Send(hm10);
          break;
        }
        case PACKET_CONFIG: {
          Serial.println("Received Config packet");
          struct ConfigPacket configPacket = *((struct ConfigPacket *)(receiver.GetPacketData()));
          if (configPacket.configField & (1 << CONFIG_TIME)) {
            Serial.print("Configuring current time: ");
            DateTime newTime(configPacket.config.unixtime);
            printDate(newTime);
            Serial.println();
            
            rtc.adjust(DateTime(configPacket.config.unixtime));
            rtcHealthy = true;  // we just updated the time, should be healthy again until power is lost again
          }
          if (configPacket.configField & (1 << CONFIG_PH_PERIOD)) {
            Serial.print("Configuring pH measurement period: ");
            Serial.println(configPacket.config.phPeriod);
            cachedConfig.phPeriod = configPacket.config.phPeriod;
          }

          if (configPacket.configField & (1 << CONFIG_STD_PH)) {
            Serial.print("Calibrating with standard pH: ");
            Serial.println(configPacket.config.stdPh);
            cachedConfig.stdPh = configPacket.config.stdPh;
          }

          if (configPacket.configField & (1 << CONFIG_STD_TEMP)) {
            Serial.print("Calibrating with standard temperature: ");
            Serial.println(configPacket.config.stdTemperature);
            cachedConfig.stdTemperature = configPacket.config.stdTemperature;

          }

          if (configPacket.config.stdLowCon != 0.0){
          
            Serial.print("Calibrating with standard low conductivity: ");
            Serial.println(configPacket.config.stdLowCon);
            cachedConfig.stdLowCon = configPacket.config.stdLowCon;

            EC.send_cmd_with_num("Cal,low,", cachedConfig.stdLowCon); //sending the command "Cal,low,#" 
            Serial.print("stdLowCon= ");
            Serial.println(cachedConfig.stdLowCon);
          }

          if(configPacket.config.stdHighCon != 0.0){
            Serial.print("Calibrating with standard high conductivity: ");
            Serial.println(configPacket.config.stdHighCon);
            cachedConfig.stdHighCon = configPacket.config.stdHighCon;

            EC.send_cmd_with_num("Cal,high,", cachedConfig.stdHighCon); //sending the command "Cal,high,#"
          }          

          EEPROM.put(0, cachedConfig);
          
          break;
        }
        case PACKET_DATA: {
          Serial.println("Received Data packet");
          // if logger has available entries (currently sending data), ignore
          if (logger.Available()) {
            Serial.println("Currently sending, ignoring...");
            break;
          }
          struct DataPacket dataPacket = *((struct DataPacket *)(receiver.GetPacketData()));
          DateTime dateLow(dataPacket.unixLow);
          DateTime dateHigh(dataPacket.unixHigh);
          printDate(DateTime(dataPacket.unixLow));
          Serial.println();
          printDate(DateTime(dataPacket.unixHigh));
          Serial.println();

          strcpy(dataLabel, dataPacket.label);
          Serial.print("This is the label: ");
          Serial.println(dataPacket.label);
          
          // open logger between dates
          logger.Open(dateLow, dateHigh, dataLabel);
          sending = true;
          break;
        }
        default: {
          Serial.print("Unknown packet id: ");
          Serial.println(receiver.GetPacketID());
          break;
        }
      }
      
      receiver.Begin();
    }
  } else {
    // nothing available, restart receiver state machine if we've stalled for too long (5 seconds)
    //Serial.println("testing restart");
    if (millis() - lastByteReadMillis > 5000) {
      //Serial.println("restart");
      lastByteReadMillis = millis();
      receiver.Begin();
    }
  }
}


void ezoRead(float temp){

  EC.send_read_with_temp_comp(temp); //sending the "rt" command with 't' being the temperature compensation 
  //EC.send_read_cmd();
  delay(700); //Need a delay of at least 7ms 
  EC.receive_read_cmd();
  //Serial.println();
  data = EC.get_buffer();
  data_string = String(data);
  //Serial.print("This is the data: ");
  //Serial.println(data_string);
  //Serial.println("Now decomposing the data...");
  data_decompose(data);
  //Serial.println();
  conductivity = atof(con);
  //Serial.print("Extracting the salinity: ");
  salinity = atof(sal); //converts the value to a float
  //Serial.println(salinity);
}

//This function will parse through the char array and extract each data by removing the comma and creating tokens as substrings
void data_decompose(char* buffer){
  token = buffer;
  //Serial.println();
  con = strtok(token, delimiter);
  //con = atof(strtok(token,delimiter));
  tds = strtok(NULL, delimiter);
  sal = strtok(NULL, delimiter);
  gravity = strtok(NULL, delimiter);
  //Serial.print("EC:");
  //Serial.println(con);
  //Serial.print("tds:");
  //Serial.println(tds);
  //Serial.print("salinity:");
  //Serial.println(sal);
  //Serial.print("Gravity:");
  //Serial.println(gravity);
}

/*
//This function will parse through the char array and extract each data by removing the space and creating tokens as substrings
void pico_read(){
  Serial1.print("MEA 1 47")
  delay(1000); //Adding a 1 second delay
  String received_string = "";
  while(Serial1.available() > 0) {
    char received_char = Serial.read();
    received_string += received_char; //need to decompose this string
  }

  token = *received_string;
  Serial.println();
  command = strtok(token, picoDelimiter);
  channel = strtok(token,picoDelimiter);
  s_value = strtok(NULL, picoDelimiter);
  R0 = strtok(NULL, picoDelimiter);
  R1 = strtok(NULL, picoDelimiter);
  R2 = strtok(NULL, picoDelimiter);
  R3 = strtok(NULL, picoDelimiter);
  R4 = strtok(NULL, picoDelimiter);
  R5 = strtok(NULL, picoDelimiter);
  R6 = strtok(NULL, picoDelimiter);
  R7 = strtok(NULL, picoDelimiter);
  R8 = strtok(NULL, picoDelimiter);
  R9 = strtok(NULL, picoDelimiter);
  R10 = strtok(NULL, picoDelimiter);
  R11 = strtok(NULL, picoDelimiter);
  R12 = strtok(NULL, picoDelimiter);
  R13 = strtok(NULL, picoDelimiter);
  R14 = strtok(NULL, picoDelimiter);
}

//This function will calibrate the pico pH. The sensor requires a two-point pH calibration so it takes in the low pH, high pH,
//and the temperature and salinity of the calibration solution
void pico_calibrate(float lowPH, float highPH, float temp, float sal){
  // "CPH C N P T S"
  String lowCommand = "CPH 1 0 " + String(lowPH) +  " " + String(temp) + " " + String(sal); 
  Serial1.println(lowCommand);
  Serial1.println("SVS 1"); //permanently saving configuration in flash memory
  delay(1000);
  String highCommand = "CPH 1 0 " + String(highPH) +  " " + String(temp) + " " + String(sal); 
  Serial1.println(highCommand);
  Serial1.println("SVS 1");
}
*/
