/*!
 * @file PacketSender.h
 * @author Sebastian S.
 * @brief Declaration for PacketSender
 */

#ifndef SEND_PACKET_H_
#define SEND_PACKET_H_

#include <Arduino.h>

/*!
 * @class PacketSender
 * @brief Builds packets and and sends them over a serial stream.
 * 
 * Packets start with a sync pattern and are of the form
 * [Length: 2 bytes] | [ID: 1 byte] | [Data: Length - 1]
 * Where Length is the byte size of the entire packet, excluding itself and the sync pattern.
 */
class PacketSender {
 public:
  /*!
   * @brief Initializes a PacketSender
   * @param buf An external buffer to use in building the packet
   * @param sync 32-bit sync pattern in little-endian to send before each packet
   */
  PacketSender(char *buf, uint32_t sync): _buf(buf), _sync(sync), _len(0), _off(0), _id(0) { }

  /*!
   * @brief Prepares the PacketSender to send a new packet.
   * @param id ID of the new packet
   */
  void Begin(uint8_t id);
  
  /*!
   * @brief Adds a data buffer to the packet.
   * @param addbuf Data buffer to add bytes from
   * @param len Length of the data buffer
   * @return The number of bytes added to the packet
   */
  int AddBuf(const char *addbuf, int len);
  
  /*!
   * @brief Adds a byte to the packet
   * @param addbyte Byte to add
   * @return The number of bytes added to the packet
   */
  int AddByte(uint8_t addbyte);
  
  /*!
   * @brief Adds a 2-byte short to the packet
   * @param addshort short to add
   * @return The number of bytes added to the packet
   */
  int AddShort(uint16_t addshort);
  
  /*!
   * @brief Adds a 4-byte long to the packet
   * @param addlong long to add
   * @return The number of bytes added to the packet
   */
  int AddLong(uint32_t addlong);
  
  /*!
   * @brief Adds a 4-byte float to the packet
   * @param addfloat float to add
   * @return The number of bytes added to the packet
   */
  int AddFloat(float addfloat);
  
  /*!
   * @brief Adds a null-terminated string to the packet
   * @param str String to add
   * @return The number of bytes added to the packet
   */
  int AddStr(const char *str);
  
  /*!
   * @brief Completes the packet being built and sends it over a serial stream
   * @param serial Reference to an AltSoftSerial stream to write the packet into
   * @return The number of bytes written by the stream
   */
  int Send(HardwareSerial& serial);
 private:
  char *    _buf;
  uint32_t  _sync;
  uint16_t  _len;
  uint16_t  _off;
  uint8_t   _id;
};

#endif
