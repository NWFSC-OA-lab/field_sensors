/*!
 * @file PacketSender.cpp
 * @author Sebastian S.
 * @Comments added by Andy X.
 * @brief Implementation of PacketSender.h
 */

#include "PacketSender.h"

void PacketSender::Begin(uint8_t id) {
  _id = id; //This is the packet id (e.g, Ping, Health, Data, Calibrate)
  _len = 0; //This is the length of the buffer. Initialized to 0
  _off = sizeof(_sync) + sizeof(_len); //This is the offset, or the current index value

  AddByte(id); //At begin, we add the id to the first index of the buffer
}

/*
This function takes in an unsigned byte and adds it to a buffer called _buff.
After the byte is added to the buffer, it increments the variables _off and _len.
_off is used to keep track of the current index in the buffer
_len is the length of the buffer
*/
int PacketSender::AddByte(uint8_t addbyte) {
  _buf[_off] = addbyte; //storing addbyte at index _buff[_off]
  _off++; //increment index number by 1
  _len++; //increment the length by 1
  return 1;
}

/*
This function takes in a pointer to a char array of bytes and an int for the len of the array.
The function returns the length of the array.
It iterates through the array and calls the AddByte() function for each element to add the byte
to a buffer
*/
int PacketSender::AddBuf(const char *addbuf, int len) { 
  //iterates through the array
  for (int i = 0; i < len; i++) {
    AddByte(addbuf[i]);
  }
  return len;
}

/*
This function takes in a uint16_t (16-bit unsigned integer). 

The AddShort() function uses the sizeof() operator to determine the size of the uint16_t data type, 
which is 2 bytes. It then casts the addshort value to a pointer to a char data type using the (char *) 
cast operator. This is done to convert the 16-bit integer value to a series of bytes that can be added 
to the data buffer.

The AddBuf() function is then called with the pointer to the converted addshort value and the size of the 
uint16_t data type. The AddBuf() function then iterates over the bytes of the addshort value and adds each
byte to the data buffer using the AddByte() function.
*/
int PacketSender::AddShort(uint16_t addshort) {
  return AddBuf((char *) &addshort, sizeof(uint16_t));
}

//This function works the same as AddShort except with a long
int PacketSender::AddLong(uint32_t addlong) {
  return AddBuf((char *) &addlong, sizeof(uint32_t));
}

//This function works the same as AddShort except with floats
int PacketSender::AddFloat(float addfloat) {
  return AddBuf((char *) &addfloat, sizeof(float));
}

//This function works the same as AddShort except with strings
int PacketSender::AddStr(const char *str) {
  int added = AddBuf(str, strlen(str));
  added += AddByte('\0');
  return added;
}

// writes length to buffer at the start, sends full packet over given serial
/*
This function takes in the HardwareSerial& serial and writes to the serial port
and returns the number of bytes written. 
*/
int PacketSender::Send(HardwareSerial& serial) {
  *((uint32_t *) _buf) = _sync;  // add sync pattern
  
  *((uint16_t *) (_buf + sizeof(_sync))) = _len;  // add length
  
  return serial.write(_buf, sizeof(_sync) + sizeof(_len) + _len);
}
