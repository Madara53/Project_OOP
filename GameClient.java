import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

/**
 * Client ของเกมกระโดดสามัคคีไม่ทัวร้อน
 *
 *  - Paint        : วาดภาพทั้งหมดใน paintComponent()
 *  - Timer        : javax.swing.Timer สั่งวาดใหม่ทุก 16 ms
 *  - Thread       : Thread แยกสำหรับอ่านสถานะเกมจาก Server
 *  - Client-Server: ส่งปุ่มที่กดไปให้ Server และวาดตามสถานะที่ Server ส่งกลับมา
 *
 * Client ไม่คำนวณฟิสิกส์เอง จึงเห็นภาพเหมือนกันทุกเครื่อง
 */
public class GameClient extends JPanel implements KeyListener {

    static final int W = 800, H = 460;
    static final int FX = 20, FY = 64;
    static final int FW = Leveldata.FW, FH = Leveldata.FH;
    static final int MAX = Leveldata.MAX_PLAYERS;

    static final Color BG = new Color(40, 30, 90);
    static final Color FIELD = new Color(220, 245, 238);
    static final Color GROUND = new Color(45, 125, 130);

    private final Object lock = new Object();             // ป้องกัน Thread อ่านเครือข่ายกับ Thread วาดภาพชนกัน
    private final Socket socket;
    private final PrintWriter out;

    // ---- สถานะที่ได้จาก Server ----
    private int myId = -1;
    private int levelNo = 1;
    private Leveldata level = Leveldata.build(1);
    private String state = "PLAY";
    private long elapsedMs = 0;
    private int deaths = 0;
    private boolean gateOpen = false;
    private int[] vfTimer = new int[0];
    private final Player[] players = new Player[MAX];
    private final boolean[] present = new boolean[MAX];
    private volatile String status = "";

    // ---- ปุ่มที่กด ----
    private boolean leftK, rightK, jumpK;
    private String lastSent = "";

    public GameClient(String host, int port) throws IOException {
        socket = new Socket(host, port);
        socket.setTcpNoDelay(true);
        out = new PrintWriter(new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), true);
        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));

        for (int i = 0; i < MAX; i++) players[i] = new Player(i + 1, Leveldata.COLORS[i], 0, 0);

        setPreferredSize(new Dimension(W, H));
        setFocusable(true);
        setFocusTraversalKeysEnabled(false);
        addKeyListener(this);

        Thread reader = new Thread(() -> readLoop(in), "net-reader");
        reader.setDaemon(true);
        reader.start();

        new javax.swing.Timer(16, e -> repaint()).start();
    }

    // ================== รับข้อมูลจาก Server (Thread แยก) ==================
    private void readLoop(BufferedReader in) {
        try {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith("W ")) {
                    myId = Integer.parseInt(line.substring(2).trim());
                } else if (line.equals("FULL")) {
                    status = "ห้องเต็มแล้ว (เล่นได้สูงสุด 4 คน)";
                } else if (line.startsWith("S|")) {
                    applyState(line);
                }
            }
        } catch (IOException ignored) {
        }
        if (status.isEmpty()) status = "ขาดการเชื่อมต่อจากเซิร์ฟเวอร์";
    }

    private void applyState(String line) {
        try {
            String[] t = line.split("\\|", -1);
            synchronized (lock) {
                int n = Integer.parseInt(t[1]);
                if (n != levelNo) { levelNo = n; level = Leveldata.build(n); }
                state = t[2];
                elapsedMs = Long.parseLong(t[3]);
                deaths = Integer.parseInt(t[4]);
                gateOpen = t[5].equals("1");

                if (t[6].isEmpty()) {
                    vfTimer = new int[0];
                } else {
                    String[] vf = t[6].split(",");
                    vfTimer = new int[vf.length];
                    for (int i = 0; i < vf.length; i++) vfTimer[i] = Integer.parseInt(vf[i]);
                }

                for (int i = 0; i < MAX; i++) {
                    String p = t[7 + i];
                    if (p.equals("-")) {
                        present[i] = false;
                    } else {
                        String[] f = p.split(":");
                        players[i].setPosition(Integer.parseInt(f[0]), Integer.parseInt(f[1]));
                        players[i].setReached(f[2].equals("1"));
                        present[i] = true;
                    }
                }
            }
        } catch (RuntimeException ex) {
            // ข้อความเสียรูปแบบ: ข้ามไป รอเฟรมถัดไป
        }
    }

    // ================== ส่งปุ่มไปให้ Server ==================
    private void sendInput() {
        String msg = "I " + (leftK ? 1 : 0) + (rightK ? 1 : 0) + (jumpK ? 1 : 0);
        if (!msg.equals(lastSent)) {
            lastSent = msg;
            out.println(msg);
        }
    }

    @Override
    public void keyPressed(KeyEvent e) {
        setKey(e.getKeyCode(), true);
        if (e.getKeyCode() == KeyEvent.VK_ENTER) out.println("N");
        if (e.getKeyCode() == KeyEvent.VK_R) out.println("R");
    }

    @Override
    public void keyReleased(KeyEvent e) { setKey(e.getKeyCode(), false); }

    @Override
    public void keyTyped(KeyEvent e) { }

    private void setKey(int code, boolean down) {
        switch (code) {
            case KeyEvent.VK_LEFT: case KeyEvent.VK_A: leftK = down; break;
            case KeyEvent.VK_RIGHT: case KeyEvent.VK_D: rightK = down; break;
            case KeyEvent.VK_UP: case KeyEvent.VK_W: case KeyEvent.VK_SPACE: jumpK = down; break;
            default: return;
        }
        sendInput();
    }

    // ================== วาดภาพ (Paint) ==================
    @Override
    protected void paintComponent(Graphics g0) {
        super.paintComponent(g0);
        Graphics2D g = (Graphics2D) g0;
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(BG);
        g.fillRoundRect(0, 0, W, H, 30, 30);

        synchronized (lock) {
            int active = 0, reached = 0;
            for (int i = 0; i < MAX; i++) {
                if (present[i]) { active++; if (players[i].hasReachedDoor()) reached++; }
            }

            // HUD
            g.setFont(new Font("Tahoma", Font.BOLD, 18));
            g.setColor(Color.WHITE);
            g.drawString("ด่าน " + levelNo + " / " + Leveldata.TOTAL_LEVELS, 24, 40);
            drawCentered(g, "ถึงประตู " + reached + " / " + active + " คน", W / 2, 40);
            g.setColor(new Color(255, 200, 60));
            String t = "เวลา " + formatTime(elapsedMs);
            g.drawString(t, W - 24 - g.getFontMetrics().stringWidth(t), 40);

            // พื้นที่เล่น
            g.setColor(FIELD);
            g.fillRect(FX, FY, FW, FH);
            Graphics2D f = (Graphics2D) g.create();
            f.translate(FX, FY);
            f.setClip(0, 0, FW, FH);
            drawWorld(f, reached);
            f.dispose();

            // ข้อความด้านล่าง
            g.setFont(new Font("Tahoma", Font.PLAIN, 13));
            g.setColor(new Color(190, 170, 240));
            String me = (myId >= 0) ? "คุณคือ P" + (myId + 1) + "   " : "";
            drawCentered(g, me + "เดิน: A D หรือ ← →   กระโดด: W ↑ Space   เริ่มด่านใหม่: R", W / 2, 430);

            if (!state.equals("PLAY")) drawOverlay(g);
        }

        if (!status.isEmpty()) {
            g.setColor(new Color(20, 15, 60, 220));
            g.fillRect(FX, FY, FW, FH);
            g.setColor(Color.WHITE);
            g.setFont(new Font("Tahoma", Font.BOLD, 26));
            drawCentered(g, status, W / 2, 240);
        }
    }

    private void drawWorld(Graphics2D g, int reached) {
        // จุดเริ่มต้น
        int sx = level.spawnX - 10, sy = level.groundY - 56, sw = MAX * 30 + 10;
        g.setColor(new Color(150, 225, 175, 120));
        g.fillRoundRect(sx, sy, sw, 56, 10, 10);
        g.setColor(new Color(50, 150, 90));
        g.setStroke(new BasicStroke(1.5f, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10f, new float[]{4f, 4f}, 0f));
        g.drawRoundRect(sx, sy, sw, 56, 10, 10);
        g.setStroke(new BasicStroke(1f));
        g.setFont(new Font("Tahoma", Font.BOLD, 12));
        g.setColor(new Color(30, 110, 70));
        drawCentered(g, "จุดเริ่มต้น", sx + sw / 2, sy - 6);

        // พื้น/แท่น
        g.setColor(GROUND);
        for (Rectangle p : level.platforms) g.fillRect(p.x, p.y, p.width, p.height);

        // พื้นหาย
        for (int i = 0; i < level.vfloors.size(); i++) {
            Rectangle r = level.vfloors.get(i).r;
            int timer = (i < vfTimer.length) ? vfTimer[i] : -1;
            boolean gone = timer >= GameServer.VANISH_DELAY;
            g.setFont(new Font("Tahoma", Font.BOLD, 12));
            g.setColor(new Color(40, 90, 100));
            if (!gone) {
                boolean blink = timer > 0 && (timer / 3) % 2 == 0;
                if (!blink) {
                    Graphics2D c = (Graphics2D) g.create();
                    c.clipRect(r.x, r.y, r.width, r.height);
                    c.setColor(new Color(170, 220, 215));
                    c.fillRect(r.x, r.y, r.width, r.height);
                    c.setColor(GROUND);
                    for (int x = r.x - r.height; x < r.x + r.width; x += 8)
                        c.drawLine(x, r.y + 400, x + 400, r.y);
                    c.dispose();
                }
            }
            drawCentered(g, "พื้นหาย", r.x + r.width / 2, r.y - 8);
        }

        // หนาม
        for (Rectangle s : level.spikes) {
            g.setColor(new Color(30, 40, 60));
            for (int x = s.x; x + 10 <= s.x + s.width; x += 10)
                g.fillPolygon(new int[]{x, x + 5, x + 10}, new int[]{s.y + s.height, s.y, s.y + s.height}, 3);
            g.setFont(new Font("Tahoma", Font.BOLD, 12));
            g.setColor(new Color(215, 40, 60));
            drawCentered(g, "หนาม", s.x + s.width / 2, s.y - 8);
        }

        // กำแพง + ปุ่ม
        for (Rectangle gate : level.gates) {
            if (!gateOpen) {
                g.setColor(new Color(200, 95, 60));
                g.fillRect(gate.x, gate.y, gate.width, gate.height);
            } else {
                g.setColor(new Color(200, 95, 60, 70));
                g.drawRect(gate.x, gate.y, gate.width, gate.height);
            }
            g.setFont(new Font("Tahoma", Font.BOLD, 12));
            g.setColor(new Color(170, 70, 40));
            drawCentered(g, "กำแพง", gate.x + gate.width / 2, gate.y - 8);
        }
        for (Rectangle b : level.buttons) {
            boolean pressed = false;
            for (int i = 0; i < MAX; i++)
                if (present[i] && players[i].getBounds().intersects(b)) pressed = true;
            g.setColor(pressed ? new Color(150, 40, 40) : new Color(225, 70, 70));
            g.fillRoundRect(b.x, pressed ? b.y + 3 : b.y, b.width, pressed ? 3 : 6, 4, 4);
            g.setFont(new Font("Tahoma", Font.BOLD, 12));
            g.setColor(new Color(170, 40, 40));
            drawCentered(g, "ปุ่ม", b.x + b.width / 2, b.y - 8);
        }

        // ประตู
        Rectangle d = level.door;
        g.setColor(new Color(110, 80, 190));
        g.fillRoundRect(d.x, d.y, d.width, d.height + 6, 26, 26);
        g.setColor(new Color(215, 195, 255));
        g.fillRoundRect(d.x + 6, d.y + 6, d.width - 12, d.height, 18, 18);
        g.setFont(new Font("Tahoma", Font.BOLD, 12));
        g.setColor(new Color(60, 40, 120));
        drawCentered(g, "ประตู", d.x + d.width / 2, d.y - 8);
        if (reached > 0) {
            g.setColor(new Color(60, 190, 110));
            g.fillOval(d.x - 20, d.y + 4, 14, 14);
            g.setColor(Color.WHITE);
            g.setStroke(new BasicStroke(2f));
            g.drawLine(d.x - 17, d.y + 11, d.x - 14, d.y + 14);
            g.drawLine(d.x - 14, d.y + 14, d.x - 9, d.y + 8);
            g.setStroke(new BasicStroke(1f));
        }

        // ผู้เล่นทุกคน (ตำแหน่งตามที่ Server ส่งมา)
        for (int i = 0; i < MAX; i++) if (present[i]) players[i].draw(g);

        // ลูกศรชี้ตัวของเราเอง
        if (myId >= 0 && myId < MAX && present[myId] && !players[myId].hasReachedDoor()) {
            int cx = (int) players[myId].getX() + Player.w / 2;
            int ty = (int) players[myId].getY() - 22;
            g.setColor(new Color(255, 200, 60));
            g.fillPolygon(new int[]{cx - 5, cx + 5, cx}, new int[]{ty, ty, ty + 7}, 3);
        }
    }

    private void drawOverlay(Graphics2D g) {
        g.setColor(new Color(20, 15, 60, 190));
        g.fillRect(FX, FY, FW, FH);
        g.setColor(Color.WHITE);
        g.setFont(new Font("Tahoma", Font.BOLD, 30));
        if (state.equals("CLEAR")) {
            drawCentered(g, "Finish Level " + levelNo + "!", W / 2, 220);
            g.setFont(new Font("Tahoma", Font.PLAIN, 18));
            drawCentered(g, " Press Enter to continue to the next level", W / 2, 260);
        } else {
            drawCentered(g, "ALL Finished!", W / 2, 200);
            g.setFont(new Font("Tahoma", Font.PLAIN, 18));
            drawCentered(g, "Total Time: " + formatTime(elapsedMs) + "   Restarts: " + deaths, W / 2, 240);
            drawCentered(g, " Press Enter to play again from level 1", W / 2, 275);
        }
    }

    private void drawCentered(Graphics2D g, String s, int cx, int y) {
        g.drawString(s, cx - g.getFontMetrics().stringWidth(s) / 2, y);
    }

    private static String formatTime(long ms) {
        long s = ms / 1000;
        return String.format("%d:%02d", s / 60, s % 60);
    }

    // ================== เริ่มโปรแกรม ==================
    public static void main(String[] args) {
        String host = (args.length > 0) ? args[0] : null;
        if (host == null) {
            host = JOptionPane.showInputDialog(null,
                    "Input IP Host GameServer\n(if Same Device use localhost)", "localhost");
        }
        if (host == null || host.trim().isEmpty()) return;
        final String h = host.trim();

        SwingUtilities.invokeLater(() -> {
            try {
                GameClient c = new GameClient(h, GameServer.PORT);
                JFrame frame = new JFrame("เกมกระโดดสามัคคีไม่ทัวร้อน (Online)");
                frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                frame.setContentPane(c);
                frame.pack();
                frame.setResizable(false);
                frame.setLocationRelativeTo(null);
                frame.setVisible(true);
                c.requestFocusInWindow();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(null, "เชื่อมต่อเซิร์ฟเวอร์ไม่ได้: " + ex.getMessage());
                System.exit(0);
            }
        });
    }
}