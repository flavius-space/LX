/**
 * Copyright 2013- Mark C. Slee, Heron Arts LLC
 *
 * This file is part of the LX Studio software library. By using
 * LX, you agree to the terms of the LX Studio Software License
 * and Distribution Agreement, available at: http://lx.studio/license
 *
 * Please note that the LX license is not open-source. The license
 * allows for free, non-commercial use.
 *
 * HERON ARTS MAKES NO WARRANTY, EXPRESS, IMPLIED, STATUTORY, OR
 * OTHERWISE, AND SPECIFICALLY DISCLAIMS ANY WARRANTY OF
 * MERCHANTABILITY, NON-INFRINGEMENT, OR FITNESS FOR A PARTICULAR
 * PURPOSE, WITH RESPECT TO THE SOFTWARE.
 *
 * @author Mark C. Slee <mark@heronarts.com>
 */

package heronarts.lx.output;

import heronarts.lx.model.LXModel;

/**
 * A datagram implementing the Kinet protocol, used by Color Kinetics devices.
 * These datagrams have a header followed by 512 bytes of color data. A port
 * number on the output device is specified, distinct from the UDP port. For
 * instance, an sPDS-480 has 16 outputs.
 */
public class KinetDatagram extends LXBufferDatagram {

  private final static int DMXOUT_HEADER_LENGTH = 21;
  private final static int PORTOUT_HEADER_LENGTH = 24;
  private final static int DATA_LENGTH = 512;

  public final static int MAX_DATA_LENGTH = DATA_LENGTH;

  private final static int PORTOUT_PACKET_LENGTH = PORTOUT_HEADER_LENGTH + DATA_LENGTH;
  private final static int DMXOUT_PACKET_LENGTH = DMXOUT_HEADER_LENGTH + DATA_LENGTH;

  private final static int KINET_PORT = 6038;

  public enum Version {
    DMXOUT,
    PORTOUT;

    public int getDataOffset() {
      switch (this) {
      case PORTOUT:
        return PORTOUT_HEADER_LENGTH;

      case DMXOUT:
      default:
        return DMXOUT_HEADER_LENGTH;
      }
    }

    public int getPacketLength() {
      switch (this) {
      case PORTOUT:
        return PORTOUT_PACKET_LENGTH;

      case DMXOUT:
      default:
        return DMXOUT_PACKET_LENGTH;
      }
    }
  };

  private final Version version;

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param model Model to output points for
   * @param kinetPort Number of the output port on the kinet power supply
   */
  public KinetDatagram(LXModel model, int kinetPort) {
    this(model, kinetPort, Version.PORTOUT);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param model Model that this datagram outputs points for
   * @param kinetPort Number of the output port on the kinet power supply
   * @param version Version of Kinet Protocol
   */
  public KinetDatagram(LXModel model, int kinetPort, Version version) {
    this(model.toIndexBuffer(), kinetPort, version);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param indexBuffer A list of the point indices that should be sent on this port
   * @param kinetPort Number of the output port on the kinet power supply
   */
  public KinetDatagram(int[] indexBuffer, int kinetPort) {
    this(indexBuffer, kinetPort, Version.PORTOUT);
  }

  /**
   * Constructs a datagram that sends on the given kinet supply output port
   *
   * @param indexBuffer Index buffer that this datagram outputs points for
   * @param kinetPort Number of the output port on the kinet power supply
   * @param version Version of Kinet Protocol
   */
  public KinetDatagram(int[] indexBuffer, int kinetPort, Version version) {
    super(indexBuffer, version.getPacketLength(), ByteOrder.RGB);
    setPort(KINET_PORT);

    this.version = version;

    // Kinet Header
    this.buffer[0] = (byte) 0x04;
    this.buffer[1] = (byte) 0x01;
    this.buffer[2] = (byte) 0xdc;
    this.buffer[3] = (byte) 0x4a;

    switch (this.version) {
    case PORTOUT:
      this.buffer[4] = (byte) 0x01;
      this.buffer[5] = (byte) 0x00;
      this.buffer[6] = (byte) 0x08;
      this.buffer[7] = (byte) 0x01;
      this.buffer[8] = (byte) 0x00;
      this.buffer[9] = (byte) 0x00;
      this.buffer[10] = (byte) 0x00;
      this.buffer[11] = (byte) 0x00;
      this.buffer[12] = (byte) 0xff;
      this.buffer[13] = (byte) 0xff;
      this.buffer[14] = (byte) 0xff;
      this.buffer[15] = (byte) 0xff;

      // Port number
      this.buffer[16] = (byte) kinetPort;

      // Maybe a checksum? 0x00 works fine
      this.buffer[17] = (byte) 0x00;
      this.buffer[18] = (byte) 0x00;
      this.buffer[19] = (byte) 0x00;
      this.buffer[20] = (byte) 0x00;

      // Total # of ports on controller? (irrelevant)
      this.buffer[21] = (byte) 0x02;

      // Unused
      this.buffer[22] = (byte) 0x00;
      this.buffer[23] = (byte) 0x00;
      break;

    case DMXOUT:
      this.buffer[4] = (byte) 0x01;
      this.buffer[5] = (byte) 0x00;

      // Type number (DMXOUT)
      this.buffer[6] = (byte) 0x01;
      this.buffer[7] = (byte) 0x01;

      // Sequence number
      this.buffer[8] = (byte) 0x00;
      this.buffer[9] = (byte) 0x00;
      this.buffer[10] = (byte) 0x00;
      this.buffer[11] = (byte) 0x00;

      // Unused header
      this.buffer[12] = (byte) 0x00;
      this.buffer[13] = (byte) 0x00;
      this.buffer[14] = (byte) 0x00;
      this.buffer[15] = (byte) 0x00;

      // Universe
      this.buffer[16] = (byte) 0xff;
      this.buffer[17] = (byte) 0xff;
      this.buffer[18] = (byte) 0xff;
      this.buffer[19] = (byte) 0xff;

      // One byte to start data
      this.buffer[20] = (byte) 0x00;
      break;
    }
  }

  public KinetDatagram setKinetPort(byte kinetPort) {
    if (this.version != Version.PORTOUT) {
      throw new IllegalStateException("Cannot set kinetPort on DMXOUT KinetDatagram");
    }
    this.buffer[16] = kinetPort;
    return this;
  }

  @Override
  protected int getColorBufferPosition() {
    return this.version.getDataOffset();
  }

}
