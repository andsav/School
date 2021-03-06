import java.net.{InetAddress, DatagramPacket, DatagramSocket}
import java.io.{File, PrintWriter}
import javax.swing.{Timer, AbstractAction}
import java.awt.event.ActionEvent
import scala.io.Source

object Sender extends App {
  require(args.length == 4)

  // Initialization
  val seqNumFile = Helpers.openFile(Constants.SEQ_NUM_LOG)
  val ackFile = Helpers.openFile(Constants.ACK_LOG)

  val (nEmulatorAddress, nEmulatorPort, senderPort, fileName) = Helpers.parseArgs(args)

  val fileContents = Helpers.getFileContents(fileName)
  val packetsToSend = Helpers.buildPackets(fileContents, Constants.MAX_READ_SIZE)
  val socket = new DatagramSocket(senderPort)

  var window = 0 until Constants.WINDOW_SIZE
  var nextSeqNum = 0

  var end = false

  // Main program
  try {
    new Thread(new PacketSender(seqNumFile, packetsToSend)).start()
    new Thread(new AckReceiver(ackFile)).start()
  }
  catch {
    case e: Exception => println("Sender aborted: " + e.toString)
  }

}

class PacketSender(log: PrintWriter, packets: Array[packet]) extends Runnable {
  val timeOut = new AbstractAction() {
    def actionPerformed(e : ActionEvent): Unit = timeout()
  }
  val timer = new Timer(Constants.TIMEOUT_DELAY, timeOut)

  def run(): Unit = {
    try {
      // Attempt to send packets until EOT received
      while(!Sender.end) {
        // Only send if the next packet falls within the window
        if(Sender.window contains Sender.nextSeqNum) {
          sendPacket(Sender.nextSeqNum)

          // (Re)start the timer if window has slidden
          if(Sender.nextSeqNum == Sender.window.head)
            timer.restart()

          Sender.nextSeqNum += 1
        }

        Thread sleep(Constants.PACKAGE_SEND_DELAY)
      }

      timer.stop()
    }
    catch {
      case e: Exception => {
        println("Packet sender aborted: " + e.toString)
      }
    }
    finally {
      log.close()
      Sender.end = true
    }
  }

  def udtSend(p: packet): Unit = {
    Helpers.udtSend(
      p,
      Sender.socket,
      Sender.nEmulatorAddress,
      Sender.nEmulatorPort)
  }

  def sendPacket(i: Int): Unit = {
    if(i < packets.length) {
      udtSend(packets(i))
      Helpers.writeLog(log, i)
      Thread sleep(Constants.PACKAGE_SEND_DELAY)
    }
  }

  def sendEOT() : Unit =
    udtSend(packet.createEOT(0))

  // This is called when timeout event occurs
  def timeout(): Unit = {
    if(!Sender.end) {
      if(Sender.window.head >= packets.length)
        sendEOT()
      else
        (Sender.window.head until Sender.nextSeqNum).foreach(sendPacket) // Re-send every un-acked packet
    }
  }
}

class AckReceiver(log: PrintWriter) extends Runnable {
  def run(): Unit = {
    try {
      var udpPacket = Helpers.emptyUdpPacket()

      while(!Sender.end) {
        Sender.socket.receive(udpPacket)
        val p = packet.parseUDPdata(udpPacket.getData())

        p.getType() match {
          case Constants.PACKET_TYPE_ACK => {

            // Ack within window
            if(Sender.window.map(x => x%32) contains p.getSeqNum) {
              val base = (Sender.window.head/32) * 32 + p.getSeqNum + 1
              Sender.window = base until (base + Constants.WINDOW_SIZE)
            }

            Helpers.writeLog(log, p.getSeqNum)
          }

          case Constants.PACKET_TYPE_EOT => Sender.end = true

          case x => println("RECEIVED invalid packet of type " + x)
        }
      }

    }
    catch {
      case e: Exception => {
        println("Ack receiver aborted: " + e.toString)
      }
    }
    finally {
      log.close()
      Sender.end = true
    }
  }
}
