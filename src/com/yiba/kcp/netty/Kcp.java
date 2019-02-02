package com.yiba.kcp.netty;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

import com.yiba.kcp.internal.ReItrLinkedList;
import com.yiba.kcp.internal.ReusableListIterator;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.util.Recycler;
import io.netty.util.internal.logging.InternalLogger;
import io.netty.util.internal.logging.InternalLoggerFactory;

/**
 * Java implementation of <a href="https://github.com/skywind3000/kcp">KCP</a>
 * KCP的策略都在这里
 * @author U-Demon
 * @date 2019年1月24日 下午6:02:54
 */
public class Kcp {
	
	private static final InternalLogger log = InternalLoggerFactory.getInstance(Kcp.class);
	
	/**
     * no delay min rto
     */
    public static final int IKCP_RTO_NDL = 30;
    
    /**
     * normal min rto
     */
    public static final int IKCP_RTO_MIN = 100;

    public static final int IKCP_RTO_DEF = 200;		//RTO超时重发

    public static final int IKCP_RTO_MAX = 60000;	//RTO最大延迟
    
    /**
     * cmd: push data 数据消息
     */
    public static final byte IKCP_CMD_PUSH = 81;

    /**
     * cmd: ack	接收回执
     */
    public static final byte IKCP_CMD_ACK = 82;

    /**
     * cmd: window probe (ask)
     */
    public static final byte IKCP_CMD_WASK = 83;

    /**
     * cmd: window size (tell)
     */
    public static final byte IKCP_CMD_WINS = 84;

    /**
     * need to send IKCP_CMD_WASK
     */
    public static final int IKCP_ASK_SEND = 1;
    
    /**
     * need to send IKCP_CMD_WINS
     */
    public static final int IKCP_ASK_TELL = 2;

    public static final int IKCP_WND_SND = 32;

    public static final int IKCP_WND_RCV = 32;

    public static final int IKCP_MTU_DEF = 1400;

    public static final int IKCP_ACK_FAST = 3;

    public static final int IKCP_INTERVAL = 100;
    
    /** KCP消息包头 */
    public static final int IKCP_OVERHEAD = 24;
    
    /** Segment.xmit超过多少则认为连接失败.原值20 */
    public static final int IKCP_DEADLINK = 20;

    public static final int IKCP_THRESH_INIT = 2;

    public static final int IKCP_THRESH_MIN = 2;

    /**
     * 7 secs to probe window size
     */
    public static final int IKCP_PROBE_INIT = 7000;

    /**
     * up to 120 secs to probe window
     */
    public static final int IKCP_PROBE_LIMIT = 120000;

    /** conv为一个表示会话编号的整数，和tcp的 conv一样，通信双方需保证 conv相同，相互的数据包才能够被认可 */
    private int conv;

    private int mtu = IKCP_MTU_DEF;	//单条消息最大长度

    private int mss = this.mtu - IKCP_OVERHEAD;	//单条消息内容最大长度,最大报文段

    private int state;

    private long sndUna;

    private long sndNxt;

    private long rcvNxt;	//滑动窗口：接收起始序号
    
//    private long tsRecent;

//    private long tsLastack;

    private int ssthresh = IKCP_THRESH_INIT;

    private int rxRttval;	//RTT的平均偏差，用来衡量RTT的抖动。消息到达延迟，rtt/2

    private int rxSrtt;	//RTT的一个加权RTT平均值，平滑值。

    private int rxRto = IKCP_RTO_DEF;	//重传超时时间,消息平均延迟

    private int rxMinrto = IKCP_RTO_MIN;

    private int sndWnd = IKCP_WND_SND;

    private int rcvWnd = IKCP_WND_RCV;	//接收窗口的大小

    private int rmtWnd = IKCP_WND_RCV;	//remote window

    private int cwnd;	//congestion window拥塞窗口

    private int probe;

    private long current;

    private int interval = IKCP_INTERVAL;	//消息发送的间隔

    private long tsFlush = IKCP_INTERVAL;

    @SuppressWarnings("unused")
	private int xmit;

    private boolean nodelay;

    private boolean updated;

    private long tsProbe;

    private int probeWait;

    private int deadLink = IKCP_DEADLINK;

    private int incr;

    private LinkedList<Segment> sndQueue = new LinkedList<>();	//发送消息队列

    private ReItrLinkedList<Segment> rcvQueue = new ReItrLinkedList<>(); //接收消息队列

    private ReItrLinkedList<Segment> sndBuf = new ReItrLinkedList<>();	//正在发送中的消息缓冲，还未收到确认回执

    private ReItrLinkedList<Segment> rcvBuf = new ReItrLinkedList<>();	//本帧接收的消息

    private ReusableListIterator<Segment> rcvQueueItr = rcvQueue.listIterator();

    private ReusableListIterator<Segment> sndBufItr = sndBuf.listIterator();

    private ReusableListIterator<Segment> rcvBufItr = rcvBuf.listIterator();

    private int[] acklist = new int[8];		//ACK的信息 sn,ts

    private int ackcount;	//需要返回确认ACK的数量

    private Object user;

    private int fastresend;		//超过多少次重发，快速重传，不用等超时

    private boolean nocwnd;

    private boolean stream;

    private KcpOutput output;

    private ByteBufAllocator byteBufAllocator = ByteBufAllocator.DEFAULT;

    /**
     * automatically set conv
     */
    private boolean autoSetConv;

    private static long long2Uint(long n) {
        return n & 0x00000000FFFFFFFFL;
    }

    private static int ibound(int lower, int middle, int upper) {
        return Math.min(Math.max(lower, middle), upper);
    }

    @SuppressWarnings("unused")
	private static long ibound(long lower, long middle, long upper) {
        return Math.min(Math.max(lower, middle), upper);
    }
    
    @SuppressWarnings("unused")
    private static int itimediff(int later, int earlier) {
        return later - earlier;
    }

    private static int itimediff(long later, long earlier) {
        return (int) (later - earlier);
    }

    private static void output(ByteBuf data, Kcp kcp) {
        if (log.isDebugEnabled()) {
            log.debug("{} [RO] {} bytes", kcp, data.readableBytes());
        }
        if (data.readableBytes() == 0) {
            return;
        }
        kcp.output.out(data, kcp);
    }

    private static int encodeSeg(ByteBuf buf, Segment seg) {//kcp头消息
        int offset = buf.writerIndex();

        buf.writeIntLE(seg.conv);
        buf.writeByte(seg.cmd);
        buf.writeByte(seg.frg);
        buf.writeShortLE(seg.wnd);
        buf.writeIntLE((int) seg.ts);
        buf.writeIntLE((int) seg.sn);
        buf.writeIntLE((int) seg.una);
        buf.writeIntLE(seg.data.readableBytes());

        return buf.writerIndex() - offset;
    }

    private static class Segment {

        private final Recycler.Handle<Segment> recyclerHandle;

        private int conv;	//连接号。UDP是无连接的，conv用于表示来自于哪个客户端。对连接的一种替代

        private byte cmd;	//命令字。如，IKCP_CMD_ACK确认命令，IKCP_CMD_WASK接收窗口大小询问命令，IKCP_CMD_WINS接收窗口大小告知命令，

        private short frg;	//分片，用户数据可能会被分成多个KCP包，发送出去。同一个包中多个segment的编号fragment n->0

        private int wnd;	//接收窗口大小，发送方的发送窗口不能超过接收方给出的数值

        private long ts;	//时间序列

        private long sn;	//序列号 0->n

        private long una;	//下一个可接收的序列号。其实就是确认号，收到sn=10的包，una为11

        private long resendts;	//下次超时重发的时刻

		private int rto;	//平均延迟，影响resendts

        private int fastack;

        private int xmit;

        private ByteBuf data;

        private static final Recycler<Segment> RECYCLER = new Recycler<Segment>() {

            @Override
            protected Segment newObject(Handle<Segment> handle) {
                return new Segment(handle);
            }

        };

        private Segment(Recycler.Handle<Segment> recyclerHandle) {
            this.recyclerHandle = recyclerHandle;
        }

        void recycle(boolean releaseBuf) {
            conv = 0;
            cmd = 0;
            frg = 0;
            wnd = 0;
            ts = 0;
            sn = 0;
            una = 0;
            resendts = 0;
            rto = 0;
            fastack = 0;
            xmit = 0;
            if (releaseBuf) {
                data.release();
            }
            data = null;

            recyclerHandle.recycle(this);
        }

        static Segment createSegment(ByteBufAllocator byteBufAllocator, int size) {
            Segment seg = RECYCLER.get();
            if (size == 0) {
                seg.data = byteBufAllocator.ioBuffer(0, 0);
            } else {
                seg.data = byteBufAllocator.ioBuffer(size);
            }
            return seg;
        }

        static Segment createSegment(ByteBuf buf) {
            Segment seg = RECYCLER.get();
            seg.data = buf;
            return seg;
        }

    }

    public Kcp(int conv, KcpOutput output) {
        this.conv = conv;
        this.output = output;
    }

    public void release() {
        release(sndBuf);
        release(rcvBuf);
        release(sndQueue);
        release(rcvQueue);
    }

    private void release(List<Segment> segQueue) {
        for (Segment seg : segQueue) {
            seg.recycle(true);
        }
    }

    private ByteBuf createFlushByteBuf() {
        return byteBufAllocator.ioBuffer(this.mtu);
    }

    /**
     * user/upper level recv: returns size, returns below zero for EAGAIN
     *
     * @param buf
     * @return
     */
    public int recv(ByteBuf buf) {
        if (rcvQueue.isEmpty()) {
            return -1;
        }
        int peekSize = peekSize();

        if (peekSize < 0) {
            return -2;
        }

        if (peekSize > buf.maxCapacity()) {
            return -3;
        }

        boolean recover = false;
        if (rcvQueue.size() >= rcvWnd) {
            recover = true;
        }

        // merge fragment
        int len = 0;
        for (Iterator<Segment> itr = rcvQueueItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            len += seg.data.readableBytes();
            buf.writeBytes(seg.data);

            int fragment = seg.frg;

            // log
            if (log.isDebugEnabled()) {
                log.debug("{} recv sn={}", this, seg.sn);
            }

            itr.remove();
            seg.recycle(true);

            if (fragment == 0) {
                break;
            }
        }

        assert len == peekSize;

        // move available data from rcv_buf -> rcv_queue
        moveRcvData();

        // fast recover
        if (rcvQueue.size() < rcvWnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe |= IKCP_ASK_TELL;
        }

        return len;
    }

    public int recv(List<ByteBuf> bufList) {
        if (rcvQueue.isEmpty()) {
            return -1;
        }
        int peekSize = peekSize();

        if (peekSize < 0) {
            return -2;
        }

        boolean recover = false;
        if (rcvQueue.size() >= rcvWnd) {
            recover = true;
        }

        // merge fragment
        int len = 0;
        for (Iterator<Segment> itr = rcvQueueItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            len += seg.data.readableBytes();
            bufList.add(seg.data);

            int fragment = seg.frg;

            // log
            if (log.isDebugEnabled()) {
                log.debug("{} recv sn={}", this, seg.sn);
            }

            itr.remove();
            seg.recycle(false);

            if (fragment == 0) {
                break;
            }
        }

        assert len == peekSize;

        // move available data from rcv_buf -> rcv_queue
        moveRcvData();

        // fast recover
        if (rcvQueue.size() < rcvWnd && recover) {
            // ready to send back IKCP_CMD_WINS in ikcp_flush
            // tell remote my window size
            probe |= IKCP_ASK_TELL;
        }

        return len;
    }

    public int peekSize() {
        if (rcvQueue.isEmpty()) {
            return -1;
        }

        Segment seg = rcvQueue.peek();
        if (seg.frg == 0) {
            return seg.data.readableBytes();
        }

        if (rcvQueue.size() < seg.frg + 1) { // Some segments have not arrived yet
            return -1;
        }

        int len = 0;
        for (Iterator<Segment> itr = rcvQueueItr.rewind(); itr.hasNext(); ) {
            Segment s = itr.next();
            len += s.data.readableBytes();
            if (s.frg == 0) {
                break;
            }
        }

        return len;
    }

    public boolean canRecv() {
        if (rcvQueue.isEmpty()) {
            return false;
        }

        Segment seg = rcvQueue.peek();
        if (seg.frg == 0) {
            return true;
        }

        if (rcvQueue.size() < seg.frg + 1) { // Some segments have not arrived yet
            return false;
        }

        return true;
    }

    public int send1(ByteBuf buf) {
        assert mss > 0;

        int len = buf.readableBytes();
        if (len == 0) {
            return -1;
        }

        // append to previous segment in streaming mode (if possible)
        if (stream) {
            if (!sndQueue.isEmpty()) {
                Segment last = sndQueue.peekLast();
                ByteBuf lastData = last.data;
                int lastLen = lastData.readableBytes();
                if (lastLen < mss) {
                    int capacity = mss - lastLen;
                    int extend = len < capacity ? len : capacity;
                    if (lastData.maxWritableBytes() < extend) { // extend
                        ByteBuf newBuf = byteBufAllocator.ioBuffer(lastLen + extend);
                        newBuf.writeBytes(lastData);
                        lastData.release();
                        lastData = last.data = newBuf;
                    }
                    lastData.writeBytes(buf, extend);

                    len = buf.readableBytes();
                    if (len == 0) {
                        return 0;
                    }
                }
            }
        }

        int count = 0;
        if (len <= mss) {
            count = 1;
        } else {
            count = (len + mss - 1) / mss;
        }

        if (count > 255) { // Maybe don't need the conditon in stream mode
            return -2;
        }

        if (count == 0) { // impossible
            count = 1;
        }

        // fragment
        for (int i = 0; i < count; i++) {
            int size = len > mss ? mss : len;
            Segment seg = Segment.createSegment(byteBufAllocator, size);
            seg.data.writeBytes(buf, size);
            seg.frg = (short) (stream ? 0 : count - i - 1);
            sndQueue.add(seg);
            len = buf.readableBytes();
        }

        return 0;
    }

    public int send(ByteBuf buf) {
        assert mss > 0;

        int len = buf.readableBytes();
        if (len == 0) {
            return -1;
        }

        // append to previous segment in streaming mode (if possible)
        if (stream) {
            if (!sndQueue.isEmpty()) {
                Segment last = sndQueue.peekLast();
                ByteBuf lastData = last.data;
                int lastLen = lastData.readableBytes();
                if (lastLen < mss) {
                    int capacity = mss - lastLen;
                    int extend = len < capacity ? len : capacity;
                    if (lastData.maxWritableBytes() < extend) { // extend
                        ByteBuf newBuf = byteBufAllocator.ioBuffer(lastLen + extend);
                        newBuf.writeBytes(lastData);
                        lastData.release();
                        lastData = last.data = newBuf;
                    }
                    lastData.writeBytes(buf, extend);

                    len = buf.readableBytes();
                    if (len == 0) {
                        return 0;
                    }
                }
            }
        }

        int count = 0;
        if (len <= mss) {
            count = 1;
        } else {
            count = (len + mss - 1) / mss;
        }

        if (count > 255) { // Maybe don't need the conditon in stream mode
            return -2;
        }

        if (count == 0) { // impossible
            count = 1;
        }

        // segment
        for (int i = 0; i < count; i++) {
            int size = len > mss ? mss : len;
            Segment seg = Segment.createSegment(buf.readRetainedSlice(size));
            seg.frg = (short) (stream ? 0 : count - i - 1);
            sndQueue.add(seg);
            len = buf.readableBytes();
        }

        return 0;
    }

    private void updateAck(int rtt) {
        if (rxSrtt == 0) {	//计算消息延迟
            rxSrtt = rtt;
            rxRttval = rtt / 2;
        } else {
            int delta = rtt - rxSrtt;
            if (delta < 0) {
                delta = -delta;
            }
            rxRttval = (3 * rxRttval + delta) / 4;
            rxSrtt = (7 * rxSrtt + rtt) / 8;
            if (rxSrtt < 1) {
                rxSrtt = 1;
            }
        }
        int rto = rxSrtt + Math.max(interval, 4 * rxRttval);
        rxRto = ibound(rxMinrto, rto, IKCP_RTO_MAX);
    }

    private void shrinkBuf() {
        if (sndBuf.size() > 0) {
            Segment seg = sndBuf.peek();
            sndUna = seg.sn;
        } else {
            sndUna = sndNxt;
        }
    }

    private void parseAck(long sn) {
        if (itimediff(sn, sndUna) < 0 || itimediff(sn, sndNxt) >= 0) {
            return;
        }

        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (sn == seg.sn) {
                itr.remove();
                seg.recycle(true);
                break;
            }
            if (itimediff(sn, seg.sn) < 0) {
                break;
            }
        }
    }

    private void parseUna(long una) {
        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (itimediff(una, seg.sn) > 0) {	//删除已经确认收到消息de缓冲
                itr.remove();
                seg.recycle(true);
            } else {
                break;
            }
        }
    }

    private void parseFastack(long sn) {
        if (itimediff(sn, sndUna) < 0 || itimediff(sn, sndNxt) >= 0) {
            return;
        }

        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (itimediff(sn, seg.sn) < 0) {
                break;
            } else if (sn != seg.sn) {
                seg.fastack++;
            }
        }
    }

    private void ackPush(long sn, long ts) {
        int newSize = 2 * (ackcount + 1);

        if (newSize > acklist.length) {
            int newCapacity = acklist.length << 1; // double capacity

            if (newCapacity < 0) {
                throw new OutOfMemoryError();
            }

            int[] newArray = new int[newCapacity];
            System.arraycopy(acklist, 0, newArray, 0, acklist.length);
            this.acklist = newArray;
        }

        acklist[2 * ackcount] = (int) sn;
        acklist[2 * ackcount + 1] = (int) ts;
        ackcount++;
    }

    private void parseData(Segment newSeg) {
        long sn = newSeg.sn;

        if (itimediff(sn, rcvNxt + rcvWnd) >= 0 || itimediff(sn, rcvNxt) < 0) {
            newSeg.recycle(true);
            return;
        }

        boolean repeat = false;
        boolean findPos = false;
        ListIterator<Segment> listItr = null;
        if (rcvBuf.size() > 0) {
            listItr = rcvBufItr.rewind(rcvBuf.size());
            while (listItr.hasPrevious()) {
                Segment seg = listItr.previous();
                if (seg.sn == sn) {
                    repeat = true;
                    break;
                }
                if (itimediff(sn, seg.sn) > 0) {
                    findPos = true;
                    break;
                }
            }
        }

        if (repeat) {
            newSeg.recycle(true);
        } else if (listItr == null) {
            rcvBuf.add(newSeg);
        } else {
            if (findPos) {
                listItr.next();
            }
            listItr.add(newSeg);
        }

        // move available data from rcv_buf -> rcv_queue
        moveRcvData(); // Invoke the method only if the segment is not repeat?
    }

    private void moveRcvData() {
        for (Iterator<Segment> itr = rcvBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            if (seg.sn == rcvNxt && rcvQueue.size() < rcvWnd) {
                itr.remove();
                rcvQueue.add(seg);
                rcvNxt++;
            } else {
                break;
            }
        }
    }

    @SuppressWarnings("unused")
	private int input1(ByteBuf data) {
        long oldSndUna = sndUna;
        long maxack = 0;
        boolean flag = false;

        if (log.isDebugEnabled()) {
            log.debug("{} [RI] {} bytes", this, data.readableBytes());
        }

        if (data == null || data.readableBytes() < IKCP_OVERHEAD) {
            return -1;
        }

        while (true) {
            int conv, len, wnd;
            long ts, sn, una;
            byte cmd;
            short frg;
            Segment seg;

            if (data.readableBytes() < IKCP_OVERHEAD) {
                break;
            }

            conv = data.readIntLE();
            if (conv != this.conv && !(this.conv == 0 && autoSetConv)) {
                return -4;
            }

            cmd = data.readByte();
            frg = data.readUnsignedByte();
            wnd = data.readUnsignedShortLE();
            ts = data.readUnsignedIntLE();
            sn = data.readUnsignedIntLE();
            una = data.readUnsignedIntLE();
            len = data.readIntLE();

            if (data.readableBytes() < len) {
                return -2;
            }

            if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
                return -3;
            }

            if (this.conv == 0 && autoSetConv) { // automatically set conv
                this.conv = conv;
            }

            this.rmtWnd = wnd;
            parseUna(una);
            shrinkBuf();

            boolean readed = false;
            long uintCurrent = long2Uint(current);
            switch (cmd) {
                case IKCP_CMD_ACK: {
                    int rtt = itimediff(uintCurrent, ts);
                    if (rtt >= 0) {
                        updateAck(rtt);
                    }
                    parseAck(sn);
                    shrinkBuf();
                    if (!flag) {
                        flag = true;
                        maxack = sn;
                    } else {
                        if (itimediff(sn, maxack) > 0) {
                            maxack = sn;
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ack: sn={}, rtt={}, rto={}", this, sn, rtt, rxRto);
                    }
                    break;
                }
                case IKCP_CMD_PUSH: {
                    if (itimediff(sn, rcvNxt + rcvWnd) < 0) {
                        ackPush(sn, ts);
                        if (itimediff(sn, rcvNxt) >= 0) {
                            seg = Segment.createSegment(byteBufAllocator, len);
                            seg.conv = conv;
                            seg.cmd = cmd;
                            seg.frg = frg;
                            seg.wnd = wnd;
                            seg.ts = ts;
                            seg.sn = sn;
                            seg.una = una;

                            if (len > 0) {
                                seg.data.writeBytes(data, len);
                                readed = true;
                            }

                            parseData(seg);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input push: sn={}, una={}, ts={}", this, sn, una, ts);
                    }
                    break;
                }
                case IKCP_CMD_WASK: {
                    // ready to send back IKCP_CMD_WINS in ikcp_flush
                    // tell remote my window size
                    probe |= IKCP_ASK_TELL;
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ask", this);
                    }
                    break;
                }
                case IKCP_CMD_WINS: {
                    // do nothing
                    if (log.isDebugEnabled()) {
                        log.debug("{} input tell: {}", this, wnd);
                    }
                    break;
                }
                default:
                    return -3;
            }

            if (!readed) {
                data.skipBytes(len);
            }
        }

        if (flag) {
            parseFastack(maxack);
        }

        if (itimediff(sndUna, oldSndUna) > 0) {
            if (cwnd < rmtWnd) {
                int mss = this.mss;
                if (cwnd < ssthresh) {
                    cwnd++;
                    incr += mss;
                } else {
                    if (incr < mss) {
                        incr = mss;
                    }
                    incr += (mss * mss) / incr + (mss / 16);
                    if ((cwnd + 1) * mss <= incr) {
                        cwnd++;
                    }
                }
                if (cwnd > rmtWnd) {
                    cwnd = rmtWnd;
                    incr = rmtWnd * mss;
                }
            }
        }

        return 0;
    }

    /**
     * 收到一个下层数据包（比如UDP包）时需要调用
     * @param data
     * @return
     */
    public int input(ByteBuf data) {
        long oldSndUna = sndUna;
        long maxack = 0;
        boolean flag = false;

        if (log.isDebugEnabled()) {
            log.debug("{} [RI] {} bytes", this, data.readableBytes());
        }

        if (data == null || data.readableBytes() < IKCP_OVERHEAD) {
            return -1;
        }

        while (true) {
            int conv, len, wnd;
            long ts, sn, una;
            byte cmd;
            short frg;
            Segment seg;

            if (data.readableBytes() < IKCP_OVERHEAD) {
                break;
            }

            conv = data.readIntLE();
            if (conv != this.conv && !(this.conv == 0 && autoSetConv)) {
                return -4;
            }

            cmd = data.readByte();
            frg = data.readUnsignedByte();
            wnd = data.readUnsignedShortLE();
            ts = data.readUnsignedIntLE();
            sn = data.readUnsignedIntLE();
            una = data.readUnsignedIntLE();
            len = data.readIntLE();

            if (data.readableBytes() < len) {
                return -2;
            }

            if (cmd != IKCP_CMD_PUSH && cmd != IKCP_CMD_ACK && cmd != IKCP_CMD_WASK && cmd != IKCP_CMD_WINS) {
                return -3;
            }

            if (this.conv == 0 && autoSetConv) { // automatically set conv
                this.conv = conv;
            }

            this.rmtWnd = wnd;
            parseUna(una);
            shrinkBuf();

            boolean readed = false;
            long uintCurrent = long2Uint(current);
            switch (cmd) {
                case IKCP_CMD_ACK: {
                    int rtt = itimediff(uintCurrent, ts);
                    if (rtt >= 0) {
                        updateAck(rtt);
                    }
                    parseAck(sn);
                    shrinkBuf();
                    if (!flag) {
                        flag = true;
                        maxack = sn;
                    } else {
                        if (itimediff(sn, maxack) > 0) {
                            maxack = sn;
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ack: sn={}, rtt={}, rto={}", this, sn, rtt, rxRto);
                    }
                    break;
                }
                case IKCP_CMD_PUSH: {
                    if (itimediff(sn, rcvNxt + rcvWnd) < 0) {	//是否超过接收窗口大小
                        ackPush(sn, ts);
                        if (itimediff(sn, rcvNxt) >= 0) {	//小于rcvNxt的消息已接收成功，不读取
                            if (len > 0) {
                                seg = Segment.createSegment(data.readRetainedSlice(len));
                                readed = true;
                            } else {
                                seg = Segment.createSegment(byteBufAllocator, 0);
                            }
                            seg.conv = conv;
                            seg.cmd = cmd;
                            seg.frg = frg;
                            seg.wnd = wnd;
                            seg.ts = ts;
                            seg.sn = sn;
                            seg.una = una;

                            parseData(seg);
                        }
                    }
                    if (log.isDebugEnabled()) {
                        log.debug("{} input push: sn={}, una={}, ts={}", this, sn, una, ts);
                    }
                    break;
                }
                case IKCP_CMD_WASK: {
                    // ready to send back IKCP_CMD_WINS in ikcp_flush
                    // tell remote my window size
                    probe |= IKCP_ASK_TELL;
                    if (log.isDebugEnabled()) {
                        log.debug("{} input ask", this);
                    }
                    break;
                }
                case IKCP_CMD_WINS: {
                    // do nothing
                    if (log.isDebugEnabled()) {
                        log.debug("{} input tell: {}", this, wnd);
                    }
                    break;
                }
                default:
                    return -3;
            }

            if (!readed) {
                data.skipBytes(len);
            }
        }

        if (flag) {
            parseFastack(maxack);
        }

        if (itimediff(sndUna, oldSndUna) > 0) {
            if (cwnd < rmtWnd) {
                int mss = this.mss;
                if (cwnd < ssthresh) {
                    cwnd++;
                    incr += mss;
                } else {
                    if (incr < mss) {
                        incr = mss;
                    }
                    incr += (mss * mss) / incr + (mss / 16);
                    if ((cwnd + 1) * mss <= incr) {
                        cwnd++;
                    }
                }
                if (cwnd > rmtWnd) {
                    cwnd = rmtWnd;
                    incr = rmtWnd * mss;
                }
            }
        }

        return 0;
    }

    private int wndUnused() {
        if (rcvQueue.size() < rcvWnd) {
            return rcvWnd - rcvQueue.size();
        }
        return 0;
    }

    /**
     * ikcp_flush
     */
    private void flush() {
        long current = this.current;
        long uintCurrent = long2Uint(current);

        // 'ikcp_update' haven't been called.
        if (!updated) {
            return;
        }

        Segment seg = Segment.createSegment(byteBufAllocator, 0);
        seg.conv = conv;
        seg.cmd = IKCP_CMD_ACK;
        seg.frg = 0;
        seg.wnd = wndUnused();
        seg.una = rcvNxt;
        seg.sn = 0;
        seg.ts = 0;

        ByteBuf buffer = createFlushByteBuf();

        // flush acknowledges
        int count = ackcount;
        for (int i = 0; i < count; i++) {
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer, this);
                buffer = createFlushByteBuf();
            }
            seg.sn = acklist[i * 2];
            seg.ts = acklist[i * 2 + 1];
            encodeSeg(buffer, seg);
            if (log.isDebugEnabled()) {
                log.debug("{} flush ack: sn={}, ts={}", this, seg.sn, seg.ts);
            }
        }

        ackcount = 0;

        // probe window size (if remote window size equals zero)
        if (rmtWnd == 0) {
            if (probeWait == 0) {
                probeWait = IKCP_PROBE_INIT;
                tsProbe = current + probeWait;
            } else {
                if (itimediff(current, tsProbe) >= 0) {
                    if (probeWait < IKCP_PROBE_INIT) {
                        probeWait = IKCP_PROBE_INIT;
                    }
                    probeWait += probeWait / 2;
                    if (probeWait > IKCP_PROBE_LIMIT) {
                        probeWait = IKCP_PROBE_LIMIT;
                    }
                    tsProbe = current + probeWait;
                    probe |= IKCP_ASK_SEND;
                }
            }
        } else {
            tsProbe = 0;
            probeWait = 0;
        }

        // flush window probing commands
        if ((probe & IKCP_ASK_SEND) != 0) {
            seg.cmd = IKCP_CMD_WASK;
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer, this);
                buffer = createFlushByteBuf();
            }
            encodeSeg(buffer, seg);
            if (log.isDebugEnabled()) {
                log.debug("{} flush ask", this);
            }
        }

        // flush window probing commands
        if ((probe & IKCP_ASK_TELL) != 0) {
            seg.cmd = IKCP_CMD_WINS;
            if (buffer.readableBytes() + IKCP_OVERHEAD > mtu) {
                output(buffer, this);
                buffer = createFlushByteBuf();
            }
            encodeSeg(buffer, seg);
            if (log.isDebugEnabled()) {
                log.debug("{} flush tell: wnd={}", this, seg.wnd);
            }
        }

        probe = 0;

        // calculate window size
        int cwnd0 = Math.min(sndWnd, rmtWnd);
        if (!nocwnd) {
            cwnd0 = Math.min(this.cwnd, cwnd0);
        }

        // move data from snd_queue to snd_buf
        while (itimediff(sndNxt, sndUna + cwnd0) < 0) {
            Segment newSeg = sndQueue.poll();
            if (newSeg == null) {
                break;
            }

            sndBuf.add(newSeg);

            newSeg.conv = conv;
            newSeg.cmd = IKCP_CMD_PUSH;
            newSeg.wnd = seg.wnd;
            newSeg.ts = uintCurrent;
            newSeg.sn = sndNxt++;
            newSeg.una = rcvNxt;
            newSeg.resendts = current;
            newSeg.rto = rxRto;
            newSeg.fastack = 0;
            newSeg.xmit = 0;
        }

        // calculate resent
        int resent = fastresend > 0 ? fastresend : Integer.MAX_VALUE;
        int rtomin = nodelay ? 0 : (rxRto >> 3);

        // flush data segments
        int change = 0;
        boolean lost = false;
        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment segment = itr.next();
            boolean needsend = false;
            if (segment.xmit == 0) {	//消息首次发送
                needsend = true;
                segment.xmit++;
                segment.rto = rxRto;
                segment.resendts = current + segment.rto + rtomin;
                if (log.isDebugEnabled()) {
                    log.debug("{} flush data: sn={}, resendts={}", this, segment.sn, (segment.resendts - current));
                }
            } else if (itimediff(current, segment.resendts) >= 0) {	//超时时间发送
                needsend = true;
                segment.xmit++;
                xmit++;
                if (!nodelay) {
                    segment.rto += rxRto;
                } else {
                    segment.rto += rxRto / 2;
                }
                segment.resendts = current + segment.rto;
                lost = true;
                if (log.isDebugEnabled()) {
                    log.debug("{} resend. sn={}, xmit={}, resendts={}", this, segment.sn, segment.xmit, (segment
                            .resendts - current));
                }
            } else if (segment.fastack >= resent) {	//快速重传次数发送
                needsend = true;
                segment.xmit++;
                segment.fastack = 0;
                segment.resendts = current + segment.rto;
                change++;
                if (log.isDebugEnabled()) {
                    log.debug("{} fastresend. sn={}, xmit={}, resendts={} ", this, segment.sn, segment.xmit, (segment
                            .resendts - current));
                }
            }

            if (needsend) {
                segment.ts = uintCurrent;
                segment.wnd = seg.wnd;
                segment.una = rcvNxt;

                ByteBuf segData = segment.data;
                int segLen = segData.readableBytes();
                int need = IKCP_OVERHEAD + segLen;

                if (buffer.readableBytes() + need > mtu) {
                    output(buffer, this);
                    buffer = createFlushByteBuf();
                }

                encodeSeg(buffer, segment);

                if (segLen > 0) {
                    // don't increases data's readerIndex, because the data may be resend.
                    buffer.writeBytes(segData, segData.readerIndex(), segLen);
                }

                if (segment.xmit >= deadLink) {
                    state = -1;
                }
            }
        }

        // flash remain segments
        if (buffer.readableBytes() > 0) {
            output(buffer, this);
        } else {
            buffer.release();
        }

        seg.recycle(true);

        // update ssthresh
        if (change > 0) {
            int inflight = (int) (sndNxt - sndUna);
            ssthresh = inflight / 2;
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN;
            }
            cwnd = ssthresh + resent;
            incr = cwnd * mss;
        }

        if (lost) {
            ssthresh = cwnd0 / 2;
            if (ssthresh < IKCP_THRESH_MIN) {
                ssthresh = IKCP_THRESH_MIN;
            }
            cwnd = 1;
            incr = mss;
        }

        if (cwnd < 1) {
            cwnd = 1;
            incr = mss;
        }
    }

    /**
     * 以一定频率调用 ikcp_update来更新 kcp状态，并且传入当前时钟（毫秒单位）
     * 如 10ms调用一次，或用 ikcp_check确定下次调用 update的时间不必每次调用
     * update getState (call it repeatedly, every 10ms-100ms), or you can ask
     * ikcp_check when to call it again (without ikcp_input/_send calling).
     * 'current' - current timestamp in millisec.
     *
     * @param current
     */
    public void update(long current) {
        this.current = current;

        if (!updated) {
            updated = true;
            tsFlush = this.current;
        }

        int slap = itimediff(this.current, tsFlush);

        if (slap >= 10000 || slap < -10000) {
            tsFlush = this.current;
            slap = 0;
        }

        /*if (slap >= 0) {
            tsFlush += setInterval;
            if (itimediff(this.current, tsFlush) >= 0) {
                tsFlush = this.current + setInterval;
            }
            flush();
        }*/

        if (slap >= 0) {
            tsFlush += interval;
            if (itimediff(this.current, tsFlush) >= 0) {
                tsFlush = this.current + interval;
            }
        } else {
            tsFlush = this.current + interval;
        }
        flush();
    }

    /**
     * Determine when should you invoke ikcp_update:
     * returns when you should invoke ikcp_update in millisec, if there
     * is no ikcp_input/_send calling. you can call ikcp_update in that
     * time, instead of call update repeatly.
     * Important to reduce unnacessary ikcp_update invoking. use it to
     * schedule ikcp_update (eg. implementing an epoll-like mechanism,
     * or optimize ikcp_update when handling massive kcp connections)
     *
     * @param current
     * @return
     */
    public long check(long current) {
        if (!updated) {
            return current;
        }

        long tsFlush = this.tsFlush;
        int slap = itimediff(current, tsFlush);
        if (slap >= 10000 || slap < -10000) {
            tsFlush = current;
            slap = 0;
        }

        if (slap >= 0) {
            return current;
        }

        int tmFlush = itimediff(tsFlush, current);
        int tmPacket = Integer.MAX_VALUE;

        for (Iterator<Segment> itr = sndBufItr.rewind(); itr.hasNext(); ) {
            Segment seg = itr.next();
            int diff = itimediff(seg.resendts, current);
            if (diff <= 0) {
                return current;
            }
            if (diff < tmPacket) {
                tmPacket = diff;
            }
        }

        int minimal = tmPacket < tmFlush ? tmPacket : tmFlush;
        if (minimal >= interval) {
            minimal = interval;
        }

        return current + minimal;
    }

    public boolean checkFlush() {
        if (ackcount > 0) {
            return true;
        }
        if (probe != 0) {
            return true;
        }
        if (sndBuf.size() > 0) {
            return true;
        }
        if (sndQueue.size() > 0) {
            return true;
        }
        return false;
    }

    public int getMtu() {
        return mtu;
    }

    public int setMtu(int mtu) {
        if (mtu < IKCP_OVERHEAD || mtu < 50) {
            return -1;
        }

        this.mtu = mtu;
        this.mss = mtu - IKCP_OVERHEAD;
        return 0;
    }

    public int getInterval() {
        return interval;
    }

    public int setInterval(int interval) {
        if (interval > 5000) {
            interval = 5000;
        } else if (interval < 10) {
            interval = 10;
        }
        this.interval = interval;

        return 0;
    }

    public int nodelay(boolean nodelay, int interval, int resend, boolean nc) {
        this.nodelay = nodelay;
        if (nodelay) {
            this.rxMinrto = IKCP_RTO_NDL;
        } else {
            this.rxMinrto = IKCP_RTO_MIN;
        }

        if (interval >= 0) {
            if (interval > 5000) {
                interval = 5000;
            } else if (interval < 10) {
                interval = 10;
            }
            this.interval = interval;
        }

        if (resend >= 0) {
            fastresend = resend;
        }

        this.nocwnd = nc;

        return 0;
    }

    public int wndsize(int sndWnd, int rcvWnd) {
        if (sndWnd > 0) {
            this.sndWnd = sndWnd;
        }
        if (rcvWnd > 0) {
            this.rcvWnd = rcvWnd;
        }

        return 0;
    }

    public int waitSnd() {
        return this.sndBuf.size() + this.sndQueue.size();
    }

    public int getConv() {
        return conv;
    }

    public void setConv(int conv) {
        this.conv = conv;
    }

    public Object getUser() {
        return user;
    }

    public void setUser(Object user) {
        this.user = user;
    }

    public int getState() {
        return state;
    }

    public void setState(int state) {
        this.state = state;
    }

    public boolean isNodelay() {
        return nodelay;
    }

    public void setNodelay(boolean nodelay) {
        this.nodelay = nodelay;
        if (nodelay) {
            this.rxMinrto = IKCP_RTO_NDL;
        } else {
            this.rxMinrto = IKCP_RTO_MIN;
        }
    }

    public int getFastresend() {
        return fastresend;
    }

    public void setFastresend(int fastresend) {
        this.fastresend = fastresend;
    }

    public boolean isNocwnd() {
        return nocwnd;
    }

    public void setNocwnd(boolean nocwnd) {
        this.nocwnd = nocwnd;
    }

    public int getRxMinrto() {
        return rxMinrto;
    }

    public void setRxMinrto(int rxMinrto) {
        this.rxMinrto = rxMinrto;
    }

    public int getRcvWnd() {
        return rcvWnd;
    }

    public void setRcvWnd(int rcvWnd) {
        this.rcvWnd = rcvWnd;
    }

    public int getSndWnd() {
        return sndWnd;
    }

    public void setSndWnd(int sndWnd) {
        this.sndWnd = sndWnd;
    }

    public boolean isStream() {
        return stream;
    }

    public void setStream(boolean stream) {
        this.stream = stream;
    }

    public int getDeadLink() {
        return deadLink;
    }

    public void setDeadLink(int deadLink) {
        this.deadLink = deadLink;
    }

    public void setByteBufAllocator(ByteBufAllocator byteBufAllocator) {
        this.byteBufAllocator = byteBufAllocator;
    }

    public boolean isAutoSetConv() {
        return autoSetConv;
    }

    public void setAutoSetConv(boolean autoSetConv) {
        this.autoSetConv = autoSetConv;
    }

    @Override
    public String toString() {
        return "Kcp(" +
                "conv=" + conv +
                ')';
    }

}
