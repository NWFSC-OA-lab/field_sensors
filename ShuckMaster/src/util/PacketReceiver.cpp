/*!
 * @file PacketReceiver.cpp
 * @author Sebastian S.
 * @brief Implementation of PacketReceiver.h
 */

#include "PacketReceiver.h"
#include "hardwareSerial.h"

/*!
 * @var bool DEBUG
 * If true, debug information should be printed over Serial during operation
 */
constexpr bool DEBUG = true;

void PacketReceiver::Begin() {
  _offset = 0;
  _plen = 0; //packet length
  _id = 0;
  _mode = Syncing;

  // if (DEBUG) Serial.println(F("Beginning packet receive"));
}

// adds the given byte to the buffer, returning true if adding the byte
// completed the packet and false otherwise
bool PacketReceiver::AddByte(uint8_t readbyte) {
  if (_toRead == 0 && _mode != Syncing) {
    return false;
  }
  switch (_mode) {
    case Syncing:
      _pattern = ((uint32_t) readbyte << 24) | (_pattern >> 8); //shifts existing pattern by one byte and sets MSB to new readbyte

      if (DEBUG) Serial.print(F("Syncing: "));
      if (DEBUG) Serial.println(_pattern, HEX);
      
      if (_pattern == _sync) { //compared with expected sync pattern to determine if beginning of packet has been found
        _mode = Length;
        _toRead = sizeof(_plen); //sets toread to size in bytes of packet length
        if (DEBUG) Serial.println(F("Sync found, moving to Length"));
      }
      break;
    case Length:
      _plen = ((uint16_t) readbyte << 8) | (_plen >> 8); //updates plen. MSB vurrent byte. LSB previous byte
      _toRead--; //one byte of length field has been read

      if (DEBUG) Serial.print(F("Length: "));
      if (DEBUG) Serial.println(_plen, HEX);
      
      if (_toRead == 0) {
        if (_plen == 0) {
          // invalid packet length, go back go back
          _mode = Syncing;
          
          if (DEBUG) Serial.println(F("Received a packet length of 0, bad packet"));
        } else {
          // we just added the last length field byte, move to data on next cycle
          _toRead = _plen;
          _mode = ID;
  
          if (DEBUG) Serial.println(F("Length complete, moving to ID"));
        }
      }
      break;
    case ID:
      _id = readbyte;
      _toRead--;
      if (_toRead == 0) {
        // we received a packet with just an ID and no data, we're done
        if (DEBUG) Serial.println(F("Packet with no data detected, complete"));
        return true;
      } else {
        if (DEBUG) Serial.println(F("ID complete, moving to Data"));
        _mode = Data;
      }
      break;
    case Data:
      _dataBuf[_offset++] = readbyte; //stores payload data at current offset index and then increments to point to next index
      _toRead--;

      if (DEBUG) Serial.print(F("Data: "));
      if (DEBUG) Serial.println(readbyte, HEX);
      
      if (_toRead == 0) {
        // just added last data byte, we're done
        if (DEBUG) Serial.println(F("Data complete, packet complete"));
        return true;
      }
      break;
    default:
      // this shouldn't happen
      break;
  }
  return false;
}

bool PacketReceiver::Completed() {
  return _toRead == 0;
}

const char *PacketReceiver::GetPacketData() {
  return _dataBuf;
}

uint16_t PacketReceiver::GetPacketDataLength() {
  return _plen - sizeof(_id);  // packet length minus id field
}

uint8_t PacketReceiver::GetPacketID() {
  return _id;
}

void PacketReceiver::PrintPacketInfo() {
  int datalen = GetPacketDataLength();
  
  Serial.println(F("Packet received"));
  
  Serial.print(F("  ID: "));
  Serial.println(_id);
  
  Serial.print(F("  Data length: "));
  Serial.println(datalen);
  
  Serial.print(F("  Received data: "));
  if (datalen == 0) {
    Serial.println(F("None"));
  } else {
    for (int i = 0; i < _plen - sizeof(_id); i++) {
      Serial.print(_dataBuf[i], HEX);
      Serial.print(F(" "));
    }
    Serial.println();
  }
}
