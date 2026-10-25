import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.List;

/**
 * ตัวละครผู้เล่นสำหรับเกมกระโดดสามัคคีไม่ทัวร้อน
 * - เดินซ้าย/ขวา (A D หรือ ← →)
 * - กระโดด (W, ↑ หรือ spacebar)
 * - ชนหนาม = ตาย (เกมต้องสั่งให้ทุกคนเริ่มใหม่)
 * - แตะประตู = ถึงเส้นชัย
 */
public class Player {

    // ---------- ค่าคงที่ ----------
    public static final int w = 24;
    public static final int h = 24;
    private  double move_speed = 3.0;
    private  double jump_speed = -9.5;
    private  double GRAVITY = 0.5;
    private  double MAX_FALL_SPEED = 12.0;

    // ---------- ข้อมูลตัวละคร ----------
    private  int id;          // 1-4 (P1-P4)
    private  String name;     // เช่น "P1"
    private  Color color;

    // ---------- ตำแหน่งและความเร็ว ----------
    private double x, y;
    private double vx, vy;
    private double spawnX, spawnY;   // จุดเริ่มต้น

    // ---------- สถานะ ----------
    private boolean onGround;
    private boolean alive = true;
    private boolean finished = false;

    // ---------- ปุ่มที่กดอยู่ ----------
    private boolean left, right, jumpPressed;

    public Player(int id, Color color, double spawnX, double spawnY) {
        this.id = id;
        this.name = "P" + id;
        this.color = color;
        this.spawnX = spawnX;
        this.spawnY = spawnY;
        respawn();
    }

    // ================== รับคำสั่งควบคุม ==================
    public void setLeft(boolean pressed)  { this.left = pressed; }
    public void setRight(boolean pressed) { this.right = pressed; }
    public void setJump(boolean pressed)  { this.jumpPressed = pressed; }

    // ================== อัปเดตทุกเฟรม ==================
    /**
     * @param platforms พื้น/แท่นที่ยืนได้
     * @param spikes    หนามที่ทำให้ตาย
     * @param door      พื้นที่ประตู
     */
    public void update(List<Rectangle> platforms, List<Rectangle> spikes, Rectangle door) {
        if (!alive || finished) return;

        // เดินซ้าย-ขวา
        vx = 0;
        if (left)  vx -= move_speed;
        if (right) vx += move_speed;

        // กระโดด (ต้องยืนบนพื้นเท่านั้น)
        if (jumpPressed && onGround) {
            vy = jump_speed;
            onGround = false;
        }

        // แรงโน้มถ่วง
        vy = Math.min(vy + GRAVITY, MAX_FALL_SPEED);

        // เคลื่อนที่ทีละแกน เพื่อให้ชนได้แม่นยำ
        moveX(platforms);
        moveY(platforms);

        // ตกหลุม (พื้นหาย) = ตาย
        if (y > 2000) {
            die();
            return;
        }

        // ชนหนาม = ตาย
        Rectangle body = getBounds();
        for (Rectangle spike : spikes) {
            if (body.intersects(spike)) {
                die();
                return;
            }
        }

        // ถึงประตู
        if (door != null && body.intersects(door)) {
            finished = true;
            vx = vy = 0;
        }
    }

    private void moveX(List<Rectangle> platforms) {
        x += vx;
        Rectangle body = getBounds();
        for (Rectangle p : platforms) {
            if (body.intersects(p)) {
                if (vx > 0) x = p.x - w;        // ชนด้านซ้ายของแท่น
                else if (vx < 0) x = p.x + p.width; // ชนด้านขวาของแท่น
                body = getBounds();
            }
        }
    }

    private void moveY(List<Rectangle> platforms) {
        y += vy;
        onGround = false;
        Rectangle body = getBounds();
        for (Rectangle p : platforms) {
            if (body.intersects(p)) {
                if (vy > 0) {            // ตกลงมาบนพื้น
                    y = p.y - h;
                    onGround = true;
                } else if (vy < 0) {     // หัวชนเพดาน
                    y = p.y + p.height;
                }
                vy = 0;
                body = getBounds();
            }
        }
    }

    // ================== ตาย / เริ่มใหม่ ==================
    /** เรียกเมื่อโดนกับดัก — เกมหลักควรเรียก respawn() ให้ผู้เล่น "ทุกคน" */
    public void die() {
        alive = false;
    }

    /** กลับไปจุดเริ่มต้น */
    public void respawn() {
        x = spawnX;
        y = spawnY;
        vx = vy = 0;
        onGround = false;
        alive = true;
        finished = false;
        left = right = jumpPressed = false;
    }

    // ================== วาดตัวละคร ==================
    public void draw(Graphics2D g) {
        int px = (int) x, py = (int) y;

        // ตัว
        g.setColor(color);
        g.fillRoundRect(px, py, w, h, 8, 8);

        // ตา
        g.setColor(Color.WHITE);
        g.fillOval(px + 5, py + 7, 5, 5);
        g.fillOval(px + 14, py + 7, 5, 5);
        g.setColor(Color.BLACK);
        g.fillOval(px + 6, py + 8, 3, 3);
        g.fillOval(px + 15, py + 8, 3, 3);

        // ชื่อ (P1, P2, ...)
        g.setColor(Color.DARK_GRAY);
        g.drawString(name, px + 2, py - 4);
    }

    // ================== ใช้ฝั่ง Client: ตั้งค่าตามที่ Server ส่งมา ==================
    public void setPosition(double x, double y) { this.x = x; this.y = y; }
    public void setReached(boolean reached)     { this.finished = reached; }

    // ================== Getter ==================
    public Rectangle getBounds() { return new Rectangle((int) x, (int) y, w, h); }
    public int getId()           { return id; }
    public String getName()      { return name; }
    public double getX()         { return x; }
    public double getY()         { return y; }
    public boolean isAlive()     { return alive; }
    public boolean isOnGround()  { return onGround; }
    public boolean hasReachedDoor() { return finished; }
}