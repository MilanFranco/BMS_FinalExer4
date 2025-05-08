package com.example.bms_finalexer4;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Bundle;
import android.os.Vibrator;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CopyOnWriteArrayList;

public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "SpaceDefenderPrefs";
    private static final String HIGH_SCORES_KEY = "highScores";

    private FrameLayout mainContainer;
    private GameView gameView;
    private View menuView;
    private View highScoreView;
    private View gameOverView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);

        mainContainer = findViewById(R.id.main);

        ViewCompat.setOnApplyWindowInsetsListener(mainContainer, (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        setupMenuView();
        setupHighScoreView();
        setupGameOverView();

        gameView = new GameView(this);
        showMenu();
    }

    private void setupMenuView() {
        menuView = getLayoutInflater().inflate(R.layout.mainmenu_layout, null);

        Button playButton = menuView.findViewById(R.id.play_button);
        Button highScoreButton = menuView.findViewById(R.id.highscore_button);
        Button quitButton = menuView.findViewById(R.id.quit_button);

        playButton.setOnClickListener(v -> startGame());
        highScoreButton.setOnClickListener(v -> showHighScore());
        quitButton.setOnClickListener(v -> finish());
    }

    private void setupHighScoreView() {
        highScoreView = getLayoutInflater().inflate(R.layout.highscore_layout, null);

        Button backButton = highScoreView.findViewById(R.id.back_button);
        backButton.setOnClickListener(v -> showMenu());
    }

    private void setupGameOverView() {
        gameOverView = getLayoutInflater().inflate(R.layout.gameover_layout, null);

        Button retryButton = gameOverView.findViewById(R.id.retry_button);
        Button menuButton = gameOverView.findViewById(R.id.menu_button);

        retryButton.setOnClickListener(v -> startGame());
        menuButton.setOnClickListener(v -> showMenu());
    }

    public void showMenu() {
        if (gameView != null) {
            gameView.pause();
        }
        mainContainer.removeAllViews();
        mainContainer.addView(menuView);
    }

    public void startGame() {
        mainContainer.removeAllViews();
        if (gameView != null) {
            gameView.resetGame();
            mainContainer.addView(gameView);
            gameView.resume();
        }
    }

    public void showHighScore() {
        mainContainer.removeAllViews();

        // Update high scores display
        LinearLayout scoresList = highScoreView.findViewById(R.id.scores_list);
        scoresList.removeAllViews();

        List<Integer> highScores = loadHighScores();

        if (highScores.isEmpty()) {
            TextView noScoresText = new TextView(this);
            noScoresText.setText("No high scores yet!");
            noScoresText.setTextColor(Color.WHITE);
            noScoresText.setTextSize(24);
            scoresList.addView(noScoresText);
        } else {
            for (int i = 0; i < highScores.size(); i++) {
                TextView scoreText = new TextView(this);
                scoreText.setText((i + 1) + ". " + highScores.get(i));
                scoreText.setTextColor(Color.WHITE);
                scoreText.setTextSize(20);
                scoresList.addView(scoreText);
            }
        }

        mainContainer.addView(highScoreView);
    }

    public void showGameOver(int finalScore) {
        TextView scoreText = gameOverView.findViewById(R.id.final_score);
        scoreText.setText("Score: " + finalScore);

        mainContainer.removeAllViews();
        mainContainer.addView(gameOverView);
    }

    public void saveHighScore(int score) {
        List<Integer> highScores = loadHighScores();

        // Add new score
        highScores.add(score);

        // Sort in descending order
        Collections.sort(highScores, Collections.reverseOrder());

        // Keep only top 10
        if (highScores.size() > 10) {
            highScores = highScores.subList(0, 10);
        }

        // Save back to SharedPreferences
        SharedPreferences.Editor editor = getSharedPreferences(PREFS_NAME, MODE_PRIVATE).edit();
        StringBuilder scoresStr = new StringBuilder();
        for (Integer hs : highScores) {
            scoresStr.append(hs).append(",");
        }
        editor.putString(HIGH_SCORES_KEY, scoresStr.toString());
        editor.apply();
    }

    private List<Integer> loadHighScores() {
        List<Integer> highScores = new ArrayList<>();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String scoresStr = prefs.getString(HIGH_SCORES_KEY, "");

        if (!scoresStr.isEmpty()) {
            String[] scores = scoresStr.split(",");
            for (String score : scores) {
                try {
                    highScores.add(Integer.parseInt(score));
                } catch (NumberFormatException e) {
                    // Skip invalid entries
                }
            }
        }

        return highScores;
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (gameView != null) {
            gameView.pause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
    }

    class GameView extends SurfaceView implements Runnable {
        // Thread and game state
        private Thread gameThread;
        private volatile boolean isPlaying;
        private MainActivity activity;
        private SurfaceHolder holder;
        private Paint paint;

        // Game objects
        private Player player;
        private CopyOnWriteArrayList<Bullet> bullets;
        private CopyOnWriteArrayList<Alien> aliens;
        private CopyOnWriteArrayList<Star> stars;
        private CopyOnWriteArrayList<Explosion> explosions;

        private int score;
        private int lives;
        private int level;
        private long lastAlienSpawn;
        private long lastBulletTime;
        private long bulletCooldown = 300;
        private Random random;

        // Screen dimensions
        private int screenWidth;
        private int screenHeight;

        // Resources
        private SoundPool soundPool;
        private int laserSound;
        private int explosionSound;
        private Vibrator vibrator;
        private Bitmap playerBitmap;
        private Bitmap alienBitmap;
        private Bitmap bulletBitmap;

        public GameView(MainActivity activity) {
            super(activity);
            this.activity = activity;
            holder = getHolder();
            paint = new Paint();
            random = new Random();

            // Initialize resources
            initResources();
        }

        private void initResources() {
            // Vibrator service
            vibrator = (Vibrator) activity.getSystemService(Context.VIBRATOR_SERVICE);

            // Sound pool
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_GAME)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();

            soundPool = new SoundPool.Builder()
                    .setMaxStreams(3)
                    .setAudioAttributes(audioAttributes)
                    .build();

            laserSound = soundPool.load(activity, R.raw.laser, 1);
            explosionSound = soundPool.load(activity, R.raw.explosion, 1);

            // Load bitmaps - scale them down for better performance
            Bitmap originalPlayer = BitmapFactory.decodeResource(getResources(), R.drawable.player);
            Bitmap originalAlien = BitmapFactory.decodeResource(getResources(), R.drawable.alien);
            Bitmap originalBullet = BitmapFactory.decodeResource(getResources(), R.drawable.bullet);

            // We'll scale them properly in resetGame when we know the screen dimensions
            playerBitmap = originalPlayer;
            alienBitmap = originalAlien;
            bulletBitmap = originalBullet;
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            screenWidth = w;
            screenHeight = h;

            // Scale bitmaps based on screen size for better performance
            if (playerBitmap != null) {
                int newWidth = (int)(w * 0.1f); // 10% of screen width
                int newHeight = (int)((float)playerBitmap.getHeight() / playerBitmap.getWidth() * newWidth);
                playerBitmap = Bitmap.createScaledBitmap(playerBitmap, newWidth, newHeight, true);
            }

            if (alienBitmap != null) {
                int newWidth = (int)(w * 0.08f); // 8% of screen width
                int newHeight = (int)((float)alienBitmap.getHeight() / alienBitmap.getWidth() * newWidth);
                alienBitmap = Bitmap.createScaledBitmap(alienBitmap, newWidth, newHeight, true);
            }

            if (bulletBitmap != null) {
                int newWidth = (int)(w * 0.02f); // 2% of screen width
                int newHeight = (int)((float)bulletBitmap.getHeight() / bulletBitmap.getWidth() * newWidth);
                bulletBitmap = Bitmap.createScaledBitmap(bulletBitmap, newWidth, newHeight, true);
            }

            resetGame();
        }

        public void resetGame() {
            // Initialize game objects using thread-safe collections
            bullets = new CopyOnWriteArrayList<>();
            aliens = new CopyOnWriteArrayList<>();
            stars = new CopyOnWriteArrayList<>();
            explosions = new CopyOnWriteArrayList<>();

            // Create player
            if (screenWidth > 0 && screenHeight > 0) {
                player = new Player(screenWidth / 2, screenHeight - 100, playerBitmap, screenWidth);

                // Create stars for background
                for (int i = 0; i < 20; i++) {  // Reduced number of stars for performance
                    stars.add(new Star(
                            random.nextInt(screenWidth),
                            random.nextInt(screenHeight),
                            random.nextInt(2) + 1  // Slower stars
                    ));
                }
            }

            score = 0;
            lives = 3;
            level = 1;
            lastAlienSpawn = 0;
            lastBulletTime = 0;
        }

        public void resume() {
            isPlaying = true;
            gameThread = new Thread(this);
            gameThread.start();
        }

        public void pause() {
            try {
                isPlaying = false;
                if (gameThread != null) {
                    gameThread.join();
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void run() {
            long startTime;
            long timeMillis;
            long waitTime;
            long targetTime = 33;

            while (isPlaying) {
                startTime = System.currentTimeMillis();

                update();

                if (holder.getSurface().isValid()) {
                    draw();
                }

                timeMillis = System.currentTimeMillis() - startTime;
                waitTime = targetTime - timeMillis;

                if (waitTime > 0) {
                    try {
                        Thread.sleep(waitTime);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        private void update() {
            if (screenWidth == 0 || screenHeight == 0) return;

            // Update level based on score
            level = 1 + score / 500;

            // Update player
            player.update();

            // Update stars
            for (Star star : stars) {
                star.update();
                if (star.y > screenHeight) {
                    star.y = 0;
                    star.x = random.nextInt(screenWidth);
                }
            }

            // Spawn aliens
            long currentTime = System.currentTimeMillis();
            int alienSpawnRate = Math.max(1500 - (level * 50), 500); // Even slower spawning for better performance
            if (currentTime - lastAlienSpawn > alienSpawnRate) {
                int x = random.nextInt(screenWidth - 50) + 25;
                int speed = Math.min(2 + (level / 4), 6); // Lower max speed for better control
                int health = 1 + (level / 5); // Slower health scaling
                aliens.add(new Alien(x, -50, speed, health, alienBitmap));
                lastAlienSpawn = currentTime;
            }

            // Update bullets and check collisions
            updateBullets();

            // Update aliens and check player collisions
            updateAliens();

            // Update explosions
            updateExplosions();
        }

        private void updateBullets() {
            for (Bullet bullet : bullets) {
                bullet.update();

                // Remove bullets that are off-screen
                if (bullet.y < 0) {
                    bullets.remove(bullet);
                    continue;
                }

                // Check collision with aliens
                for (Alien alien : aliens) {
                    if (bullet.intersects(alien)) {
                        alien.health--;

                        if (alien.health <= 0) {
                            // Add explosion
                            explosions.add(new Explosion(alien.x, alien.y));

                            // Play sound
                            soundPool.play(explosionSound, 0.5f, 0.5f, 1, 0, 1);

                            // Vibrate briefly
                            if (vibrator != null && vibrator.hasVibrator()) {
                                vibrator.vibrate(20);
                            }

                            aliens.remove(alien);
                            score += 10 * level;
                        }

                        bullets.remove(bullet);
                        break;
                    }
                }
            }
        }

        private void updateAliens() {
            for (Alien alien : aliens) {
                alien.update();

                // Check if alien reached bottom
                if (alien.y > screenHeight) {
                    aliens.remove(alien);
                    lives--;

                    if (lives <= 0) {
                        gameOver();
                        return;
                    }

                    // Vibrate to indicate life lost
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(30);
                    }
                    continue;
                }

                // Check collision with player
                if (player.intersects(alien)) {
                    aliens.remove(alien);
                    lives--;

                    // Add explosion
                    explosions.add(new Explosion(alien.x, alien.y));

                    // Play sound
                    soundPool.play(explosionSound, 0.5f, 0.5f, 1, 0, 1);

                    // Vibrate
                    if (vibrator != null && vibrator.hasVibrator()) {
                        vibrator.vibrate(30);
                    }

                    if (lives <= 0) {
                        gameOver();
                        return;
                    }
                }
            }
        }

        private void updateExplosions() {
            for (Explosion explosion : explosions) {
                explosion.update();

                if (explosion.isFinished()) {
                    explosions.remove(explosion);
                }
            }
        }

        private void gameOver() {
            isPlaying = false;
            activity.saveHighScore(score);
            activity.runOnUiThread(() -> {
                if (activity != null && !activity.isFinishing()) {
                    // Show game over screen
                    activity.showGameOver(score);
                }
            });
        }

        private void draw() {
            Canvas canvas = holder.lockCanvas();
            if (canvas != null) {
                try {
                    // Draw background
                    canvas.drawColor(Color.BLACK);

                    // Draw stars
                    for (Star star : stars) {
                        star.draw(canvas, paint);
                    }

                    // Draw player
                    player.draw(canvas, paint);

                    // Draw bullets
                    for (Bullet bullet : bullets) {
                        bullet.draw(canvas, paint);
                    }

                    // Draw aliens
                    for (Alien alien : aliens) {
                        alien.draw(canvas, paint);
                    }

                    // Draw explosions
                    for (Explosion explosion : explosions) {
                        explosion.draw(canvas, paint);
                    }

                    // Draw HUD
                    drawHUD(canvas);
                } finally {
                    holder.unlockCanvasAndPost(canvas);
                }
            }
        }

        private void drawHUD(Canvas canvas) {
            // Draw score
            paint.setColor(Color.WHITE);
            paint.setTextSize(40);
            paint.setTypeface(Typeface.DEFAULT_BOLD);
            canvas.drawText("Score: " + score, 20, 50, paint);

            // Draw level
            canvas.drawText("Level: " + level, 20, 100, paint);

            // Draw lives
            paint.setColor(Color.RED);
            for (int i = 0; i < lives; i++) {
                canvas.drawRect(
                        screenWidth - 60 + (i * -30),
                        30,
                        screenWidth - 40 + (i * -30),
                        50,
                        paint
                );
            }
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_MOVE:
                    if (event.getX() < screenWidth / 2) {
                        player.setMoveLeft(true);
                        player.setMoveRight(false);
                    } else {
                        player.setMoveRight(true);
                        player.setMoveLeft(false);
                    }

                    fireBullet();
                    return true;

                case MotionEvent.ACTION_UP:
                    player.setMoveLeft(false);
                    player.setMoveRight(false);
                    return true;
            }

            return super.onTouchEvent(event);
        }

        private void fireBullet() {
            long currentTime = System.currentTimeMillis();

            if (currentTime - lastBulletTime > bulletCooldown) {
                bullets.add(new Bullet(player.x + player.width / 2, player.y, bulletBitmap));
                soundPool.play(laserSound, 0.2f, 0.2f, 1, 0, 1); // Lower volume for better experience
                lastBulletTime = currentTime;
            }
        }
    }

    // Game object classes

    class GameObject {
        protected int x, y;
        protected int width, height;
        protected int color;
        protected Bitmap bitmap;

        public GameObject(int x, int y, int width, int height, int color) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
            this.color = color;
        }

        public GameObject(int x, int y, Bitmap bitmap) {
            this.x = x;
            this.y = y;
            this.bitmap = bitmap;
            if (bitmap != null) {
                this.width = bitmap.getWidth();
                this.height = bitmap.getHeight();
            } else {
                this.width = 30;
                this.height = 30;
                this.color = Color.WHITE;
            }
        }

        public void draw(Canvas canvas, Paint paint) {
            if (bitmap != null) {
                canvas.drawBitmap(bitmap, x - width/2, y - height/2, paint);
            } else {
                paint.setColor(color);
                canvas.drawRect(x - width/2, y - height/2, x + width/2, y + height/2, paint);
            }
        }

        public boolean intersects(GameObject other) {
            int halfWidth = width / 2;
            int halfHeight = height / 2;
            int otherHalfWidth = other.width / 2;
            int otherHalfHeight = other.height / 2;

            return Math.abs(x - other.x) < halfWidth + otherHalfWidth &&
                    Math.abs(y - other.y) < halfHeight + otherHalfHeight;
        }
    }

    class Player extends GameObject {
        private boolean moveLeft;
        private boolean moveRight;
        private int speed;
        private int screenWidth;

        public Player(int x, int y, Bitmap bitmap, int screenWidth) {
            super(x, y, bitmap);
            if (bitmap == null) {
                this.width = 40;
                this.height = 60;
                this.color = Color.GRAY;
            }

            this.speed = 10;
            this.moveLeft = false;
            this.moveRight = false;
            this.screenWidth = screenWidth;
        }

        public void update() {
            if (moveLeft && x > width/2) {
                x -= speed;
            }
            if (moveRight && x < screenWidth - width/2) {
                x += speed;
            }
        }

        public void setMoveLeft(boolean moveLeft) {
            this.moveLeft = moveLeft;
        }

        public void setMoveRight(boolean moveRight) {
            this.moveRight = moveRight;
        }
    }

    class Bullet extends GameObject {
        private int speed = 15;

        public Bullet(int x, int y, Bitmap bitmap) {
            super(x, y, bitmap);
            if (bitmap == null) {
                this.width = 6;
                this.height = 16;
                this.color = Color.YELLOW;
            }
        }

        public void update() {
            y -= speed;
        }
    }

    class Alien extends GameObject {
        private int speed;
        private int health;

        public Alien(int x, int y, int speed, int health, Bitmap bitmap) {
            super(x, y, bitmap);
            if (bitmap == null) {
                this.width = 40;
                this.height = 40;
                this.color = Color.GREEN;
            }
            this.speed = speed;
            this.health = health;
        }

        public void update() {
            y += speed;
        }
    }

    class Star {
        public int x, y;
        public int speed;
        public int size;

        public Star(int x, int y, int speed) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.size = 1; // Smaller stars for better performance
        }

        public void update() {
            y += speed;
        }

        public void draw(Canvas canvas, Paint paint) {
            paint.setColor(Color.WHITE);
            canvas.drawRect(x, y, x + size, y + size, paint);
        }
    }

    class Explosion {
        private int x, y;
        private int radius;
        private int maxRadius;
        private int alpha;

        public Explosion(int x, int y) {
            this.x = x;
            this.y = y;
            this.radius = 5;
            this.maxRadius = 20; // Smaller explosions for better performance
            this.alpha = 255;
        }

        public void update() {
            radius += 2;
            alpha -= 15;
            if (alpha < 0) alpha = 0;
        }

        public boolean isFinished() {
            return radius >= maxRadius;
        }

        public void draw(Canvas canvas, Paint paint) {
            // Simplified explosion drawing
            paint.setColor(Color.RED);
            paint.setAlpha(alpha);
            canvas.drawCircle(x, y, radius, paint);

            paint.setColor(Color.YELLOW);
            paint.setAlpha(alpha);
            canvas.drawCircle(x, y, radius / 2, paint);
        }
    }
}