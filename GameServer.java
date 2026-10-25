import java.awt.Rectangle;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Server ของเกมกระโดดสามัคคีไม่ทัวร้อน (Client-Server)
 *
 * หลักการ:
 *  - Server เป็นผู้คำนวณฟิสิกส์/กติกาทั้งหมดเพียงที่เดียว (Authoritative Server)
 *  - Client ส่งแค่ "ปุ่มที่กดอยู่" มาให้ Server
 *  - Server ส่ง "สถานะเกมทั้งหมด" กลับไปให้ทุก Client ทุก ๆ 16 ms
 *    ทำให้หน้าจอทุกเครื่องเห็นตำแหน่งผู้เล่น พื้นหาย กำแพง เวลา เหมือนกันเสมอ
 *
 * Multi-Tasking (Thread):
 *  - Thread หลัก        : รอรับ Client ใหม่ (accept)
 *  - Thread เกมลูป      : อัปเดตเกมและส่งสถานะ ~60 ครั้ง/วินาที
 *  - Thread ต่อ Client  : อ่านคำสั่งจาก Client แต่ละคน
 *
 * โปรโตคอล (ข้อความ 1 บรรทัด):
 *   Client -> Server : "I 101"  (ซ้าย ขวา กระโดด)   "R" (เริ่มด่านใหม่)   "N" (ไปต่อ)
 *   Server -> Client : "W <slot>"  (บอกว่าเป็นผู้เล่นคนที่เท่าไร)   "FULL"
 *                      "S|ด่าน|สถานะ|เวลา|ครั้งที่ตาย|กำแพงเปิด|timerพื้นหาย|P0|P1|P2|P3"
 */
public class GameServer {

    static final int PORT = 5000;
    static final int VANISH_DELAY = 30;                   // เฟรมที่พื้นหายหลังถูกเหยียบ
    static final int MAX = Leveldata.MAX_PLAYERS;

    private final Handler[] slots = new Handler[MAX];     // Client ที่เชื่อมต่ออยู่
    private final Player[] players = new Player[MAX];     // null = ช่องว่าง
    private Leveldata level;
    private int levelNo = 1;
    private String state = "PLAY";                        // PLAY / CLEAR / WIN
    private boolean gateOpen = false;
    private long elapsedMs = 0, lastTick = System.currentTimeMillis();
    private int deaths = 0;

    public GameServer() {
        loadLevel(1);
    }

    // ================== จัดการ Client แต่ละคน ==================
    class Handler implements Runnable {
        final int slot;
        final Socket socket;
        final PrintWriter out;
        final BufferedReader in;
        volatile boolean left, right, jump;

        Handler(int slot, Socket socket) throws IOException {
            this.slot = slot;
            this.socket = socket;
            this.out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
            this.in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
        }

        void send(String msg) {
            out.println(msg);
            if (out.checkError()) {
                try { socket.close(); } catch (IOException ignored) { }
            }
        }

        @Override
        public void run() {
            try {
                String line;
                while ((line = in.readLine()) != null) handleLine(this, line);
            } catch (IOException ignored) {
            } finally {
                removeClient(this);
            }
        }
    }

    private synchronized void handleLine(Handler h, String line) {
        if (line.startsWith("I ") && line.length() >= 5) {
            h.left = line.charAt(2) == '1';
            h.right = line.charAt(3) == '1';
            h.jump = line.charAt(4) == '1';
        } else if (line.equals("R")) {
            if (state.equals("PLAY")) loadLevel(levelNo);
        } else if (line.equals("N")) {
            if (state.equals("CLEAR")) {
                loadLevel(levelNo + 1);
            } else if (state.equals("WIN")) {
                elapsedMs = 0;
                deaths = 0;
                loadLevel(1);
            }
        }
    }

    private synchronized Handler addClient(Socket s) throws IOException {
        for (int i = 0; i < MAX; i++) {
            if (slots[i] == null) {
                Handler h = new Handler(i, s);
                slots[i] = h;
                h.send("W " + i);
                loadLevel(levelNo);                       // มีคนเข้าใหม่ = เริ่มด่านใหม่ทุกคน
                System.out.println("ผู้เล่น P" + (i + 1) + " Join (" + s.getRemoteSocketAddress() + ")");
                return h;
            }
        }
        return null;
    }

    private synchronized void removeClient(Handler h) {
        if (slots[h.slot] == h) {
            slots[h.slot] = null;
            loadLevel(levelNo);
            System.out.println("ผู้เล่น P" + (h.slot + 1) + " Close (" + h.socket.getRemoteSocketAddress() + ")");
        }
        try { h.socket.close(); } catch (IOException ignored) { }
    }

    // ================== ตรรกะเกม ==================
    private void loadLevel(int n) {
        levelNo = n;
        level = Leveldata.build(n);
        for (int i = 0; i < MAX; i++) {
            players[i] = (slots[i] == null) ? null
                    : new Player(i + 1, Leveldata.COLORS[i], level.spawnX + i * 30, level.groundY - Player.h);
        }
        gateOpen = false;
        state = "PLAY";
    }

    private int activeCount() {
        int c = 0;
        for (Player p : players) if (p != null) c++;
        return c;
    }

    private int reachedCount() {
        int c = 0;
        for (Player p : players) if (p != null && p.hasReachedDoor()) c++;
        return c;
    }

    private void loop() {
        while (true) {
            tick();
            try { Thread.sleep(16); } catch (InterruptedException e) { return; }
        }
    }

    private synchronized void tick() {
        long now = System.currentTimeMillis();
        long dt = now - lastTick;
        lastTick = now;

        if (activeCount() == 0) return;
        if (state.equals("PLAY")) {
            elapsedMs += dt;
            update();
        }
        broadcast();
    }

    private void update() {
        // มีคนเหยียบปุ่มไหม -> กำแพงเปิด
        gateOpen = false;
        for (Rectangle b : level.buttons)
            for (Player p : players)
                if (p != null && p.getBounds().intersects(b)) gateOpen = true;

        List<Rectangle> solids = new ArrayList<>(level.platforms);
        for (Leveldata.VFloor v : level.vfloors) if (!v.gone) solids.add(v.r);
        if (!gateOpen) solids.addAll(level.gates);

        for (int i = 0; i < MAX; i++) {
            Player p = players[i];
            if (p == null) continue;
            Handler h = slots[i];
            boolean free = !p.hasReachedDoor();
            p.setLeft(free && h.left);
            p.setRight(free && h.right);
            p.setJump(free && h.jump);
            p.update(solids, level.spikes, level.door);
        }

        // พื้นหาย
        for (Leveldata.VFloor v : level.vfloors) {
            if (v.gone) continue;
            if (v.timer < 0) {
                for (Player p : players) {
                    if (p == null || !p.isAlive()) continue;
                    Rectangle feet = new Rectangle((int) p.getX(), (int) p.getY() + Player.h, Player.w, 1);
                    if (feet.intersects(v.r)) { v.timer = 0; break; }
                }
            } else if (++v.timer >= VANISH_DELAY) {
                v.gone = true;
            }
        }

        // ใครตายคนเดียว = ทุกคนเริ่มใหม่
        for (Player p : players) {
            if (p != null && (!p.isAlive() || p.getY() > Leveldata.FH + 40)) {
                deaths++;
                loadLevel(levelNo);
                return;
            }
        }

        // ทุกคนถึงประตู = ผ่านด่าน
        if (reachedCount() == activeCount()) {
            state = (levelNo >= Leveldata.TOTAL_LEVELS) ? "WIN" : "CLEAR";
        }
    }

    private void broadcast() {
        List<String> parts = new ArrayList<>();
        parts.add("S");
        parts.add(String.valueOf(levelNo));
        parts.add(state);
        parts.add(String.valueOf(elapsedMs));
        parts.add(String.valueOf(deaths));
        parts.add(gateOpen ? "1" : "0");

        StringBuilder vf = new StringBuilder();
        for (int i = 0; i < level.vfloors.size(); i++) {
            if (i > 0) vf.append(',');
            vf.append(level.vfloors.get(i).timer);
        }
        parts.add(vf.toString());

        for (int i = 0; i < MAX; i++) {
            Player p = players[i];
            if (p == null) parts.add("-");
            else parts.add(Math.round(p.getX()) + ":" + Math.round(p.getY()) + ":" + (p.hasReachedDoor() ? 1 : 0));
        }

        String msg = String.join("|", parts);
        for (Handler h : slots) if (h != null) h.send(msg);
    }

    // ================== เริ่มโปรแกรม ==================
    public static void main(String[] args) throws IOException {
        GameServer server = new GameServer();
        new Thread(server::loop, "game-loop").start();

        ServerSocket ss = new ServerSocket(PORT);
        System.out.println("=== เซิร์ฟเวอร์เริ่มทำงานที่พอร์ต " + PORT + " ===");
        System.out.println("ให้เพื่อนเปิด GameClient แล้วใส่ IP ใดIPหนึ่งด้านล่างนี้:");
        for (Enumeration<NetworkInterface> ifs = NetworkInterface.getNetworkInterfaces(); ifs.hasMoreElements(); ) {
            NetworkInterface ni = ifs.nextElement();
            if (!ni.isUp() || ni.isLoopback()) continue;
            for (Enumeration<InetAddress> as = ni.getInetAddresses(); as.hasMoreElements(); ) {
                InetAddress a = as.nextElement();
                if (a instanceof Inet4Address) System.out.println("   " + a.getHostAddress() + "   (" + ni.getDisplayName() + ")");
            }
        }

        while (true) {
            Socket s = ss.accept();
            s.setTcpNoDelay(true);
            Handler h = server.addClient(s);
            if (h == null) {
                PrintWriter pw = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), StandardCharsets.UTF_8), true);
                pw.println("FULL");
                s.close();
            } else {
                new Thread(h, "client-" + h.slot).start();
            }
        }
    }
}