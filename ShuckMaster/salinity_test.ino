#include <Ezo_i2c.h> //include the EZO I2C library from https://github.com/Atlas-Scientific/Ezo_I2c_lib
#include <Wire.h>    //include arduinos i2c library
#include <Ezo_i2c_util.h> //brings in common print statements

Ezo_board EC = Ezo_board(100, "EC");      //create an EC circuit object who's address is 100 and name is "EC"
//char sensordata[];
//const static uint8_t bufferlen = 32;
char* data;
String data_string;
char* token;
const char *delimiter =","; //Used to parse through the data
//float con;
char* con;
char* tds;
char* sal;
char* gravity;
float salinity;

void setup() {
  Wire.begin();                           //start the I2C
  Serial.begin(9600);                     //start the serial communication to the computer
}

void loop() {

  EC.send_read_cmd();
  delay(700); //Need a delay of at least 7ms when we send the "R" command 
  //receive_and_print_reading(EC);             //get the reading from the EC circuit
  EC.receive_read_cmd();
  Serial.println();
  data = EC.get_buffer();
  data_string = String(data);
  Serial.print("This is the data: ");
  Serial.println(data_string);

  Serial.println("Now decomposing the data...");
  data_decompose(data);
  Serial.println();
  
  Serial.print("Extracting the salinity: ");
  salinity = atof(sal); //converts the value to a float
  Serial.println(salinity);

}

//This function will parse through the char array and extract each data by removing the comma and creating tokens as substrings
void data_decompose(char* buffer){
  token = buffer;
  Serial.println();
  con = strtok(token, delimiter);
  //con = atof(strtok(token,delimiter));
  tds = strtok(NULL, delimiter);
  sal = strtok(NULL, delimiter);
  gravity = strtok(NULL, delimiter);
  Serial.print("EC:");
  Serial.println(con);
  Serial.print("tds:");
  Serial.println(tds);
  Serial.print("salinity:");
  Serial.println(sal);
  Serial.print("Gravity:");
  Serial.println(gravity);
}
