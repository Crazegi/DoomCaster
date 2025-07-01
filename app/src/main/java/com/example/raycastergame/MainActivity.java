package com.example.raycastergame; // Make sure this matches your project's package name

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;

// --- Main Activity Class ---
public class MainActivity extends AppCompatActivity {

    private GameView gameView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        gameView = new GameView(this);
        setContentView(gameView);

        // --- HIDE SYSTEM BARS FOR IMMERSIVE FULL-SCREEN ---
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            final WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            // For older APIs
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN);
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        gameView.pause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        gameView.resume();
    }

    //==============================================================================================
    // --- GAMEVIEW CLASS START ---
    //==============================================================================================
    public class GameView extends View {

        private enum QualityLevel { HIGH, MEDIUM, LOW }
        private enum GameState { MAIN_MENU, PLAYING, PAUSED, SETTINGS, AUTHORS, GAME_OVER }
        private GameState currentState = GameState.MAIN_MENU;

        public Paint paint;
        private Handler handler;
        private Runnable gameLoop;
        private boolean isPlaying = false;
        private double[] depthBuffer;

        public final int MAP_SIZE = 64;
        public int[][] worldMap;
        public PointF playerPos;
        private double playerAngle;
        private int playerHealth;
        public int score;
        private int bestScore;
        private Random random = new Random();
        private int level = 1;
        public List<Sprite> sprites = new ArrayList<>();
        private List<Sprite> spritesToAdd = new ArrayList<>();


        private PointF moveVector = new PointF(0, 0);

        private RectF playButton, settingsButton, authorsButton, backButton;
        private RectF fovUpButton, fovDownButton, sensUpButton, sensDownButton, qualityButton;
        private RectF pauseButton;
        private RectF resumeButton, quitButton;

        private PointF joystickBase = new PointF();
        private PointF joystickKnob = new PointF();
        private PointF joystickDefaultPos = new PointF();
        private float joystickRadius;
        private int joystickPointerId = -1;
        private int lookPointerId = -1;
        private float lastLookX;
        private RectF shootButton;

        private float lookSensitivity = 0.003f;
        private float fieldOfView = 66.0f;
        private QualityLevel graphicsQuality = QualityLevel.MEDIUM;

        private int shootTimer = 0;
        private static final int WEAPON_COOLDOWN_FRAMES = 10;

        private List<Texture> textures = new ArrayList<>();

        private Paint uiPaint, textPaint, titlePaint;
        private int pressedButton = 0;

        private RectF reusableSpriteRect = new RectF();
        private RectF reusableHealthBarRect = new RectF();

        public GameView(Context context) {
            super(context);
            paint = new Paint();
            paint.setAntiAlias(false);

            uiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            textPaint.setColor(Color.WHITE);
            textPaint.setTextAlign(Paint.Align.CENTER);
            textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));

            titlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            titlePaint.setColor(Color.WHITE);
            titlePaint.setTextAlign(Paint.Align.CENTER);
            titlePaint.setTypeface(Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD));
            titlePaint.setShadowLayer(10, 0, 0, Color.BLACK);

            loadBestScore();
            loadTextures();
            handler = new Handler();
            gameLoop = () -> {
                if (isPlaying) {
                    update();
                    invalidate();
                    handler.postDelayed(gameLoop, 16);
                }
            };
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            depthBuffer = new double[w];
            float buttonWidth = w / 2.2f;
            float buttonHeight = h / 11f;
            float centerX = w / 2f;
            float startY = h / 2.8f;

            playButton = new RectF(centerX - buttonWidth / 2, startY, centerX + buttonWidth / 2, startY + buttonHeight);
            settingsButton = new RectF(centerX - buttonWidth / 2, startY + buttonHeight * 1.3f, centerX + buttonWidth / 2, startY + buttonHeight * 2.3f);
            authorsButton = new RectF(centerX - buttonWidth / 2, startY + buttonHeight * 2.6f, centerX + buttonWidth / 2, startY + buttonHeight * 3.6f);

            float settingsStartY = h / 5f;
            qualityButton = new RectF(centerX - buttonWidth / 2, settingsStartY + buttonHeight * 1.5f, centerX + buttonWidth / 2, settingsStartY + buttonHeight * 2.5f);
            float settingWidth = w / 4f;
            fovDownButton = new RectF(centerX - settingWidth * 1.5f, settingsStartY + buttonHeight * 3.5f, centerX - settingWidth * 0.5f, settingsStartY + buttonHeight * 4.5f);
            fovUpButton = new RectF(centerX + settingWidth * 0.5f, settingsStartY + buttonHeight * 3.5f, centerX + settingWidth * 1.5f, settingsStartY + buttonHeight * 4.5f);
            sensDownButton = new RectF(centerX - settingWidth * 1.5f, settingsStartY + buttonHeight * 5.5f, centerX - settingWidth * 0.5f, settingsStartY + buttonHeight * 6.5f);
            sensUpButton = new RectF(centerX + settingWidth * 0.5f, settingsStartY + buttonHeight * 5.5f, centerX + settingWidth * 1.5f, settingsStartY + buttonHeight * 6.5f);

            backButton = new RectF(centerX - buttonWidth / 2, h - buttonHeight * 2.5f, centerX + buttonWidth / 2, h - buttonHeight * 1.5f);

            resumeButton = new RectF(centerX - buttonWidth / 2, h/2 - buttonHeight, centerX + buttonWidth / 2, h/2);
            quitButton = new RectF(centerX - buttonWidth / 2, h/2 + buttonHeight * 0.3f, centerX + buttonWidth / 2, h/2 + buttonHeight * 1.3f);

            int pauseSize = h / 15;
            pauseButton = new RectF(w - pauseSize * 1.5f, pauseSize * 0.5f, w - pauseSize * 0.5f, pauseSize * 1.5f);

            joystickRadius = h / 6f;
            joystickDefaultPos.set(joystickRadius * 1.5f, h - joystickRadius * 1.5f);
            joystickBase.set(joystickDefaultPos);
            joystickKnob.set(joystickBase);

            int shootButtonSize = h / 4;
            shootButton = new RectF(w - shootButtonSize * 1.5f, h - shootButtonSize * 1.5f, w - shootButtonSize * 0.5f, h - shootButtonSize * 0.5f);
        }

        private void update() {
            if (currentState != GameState.PLAYING) return;

            if (shootTimer > 0) shootTimer--;

            for (Sprite s : sprites) {
                s.distToPlayer = Math.hypot(playerPos.x - s.x, playerPos.y - s.y);
            }
            for (Sprite s : spritesToAdd) {
                s.distToPlayer = Math.hypot(playerPos.x - s.x, playerPos.y - s.y);
            }

            float moveSpeed = 0.05f;
            if (moveVector.length() > 0.01) {
                float forwardX = (float)Math.cos(playerAngle) * moveVector.y * moveSpeed;
                float forwardY = (float)Math.sin(playerAngle) * moveVector.y * moveSpeed;
                float strafeX = (float)Math.cos(playerAngle + Math.PI / 2) * moveVector.x * moveSpeed;
                float strafeY = (float)Math.sin(playerAngle + Math.PI / 2) * moveVector.x * moveSpeed;
                float newX = playerPos.x + forwardX + strafeX;
                float newY = playerPos.y + forwardY + strafeY;
                handleCollisionAndMove(newX, newY);
            }

            for (int i = sprites.size() - 1; i >= 0; i--) {
                Sprite s = sprites.get(i);
                s.update();
                if (!s.isAlive) {
                    sprites.remove(i);
                }
            }

            if (!spritesToAdd.isEmpty()) {
                sprites.addAll(spritesToAdd);
                spritesToAdd.clear();
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);

            if (currentState == GameState.PLAYING || currentState == GameState.PAUSED || currentState == GameState.GAME_OVER) {
                drawGame(canvas);
            }

            switch (currentState) {
                case MAIN_MENU: drawMainMenu(canvas); break;
                case SETTINGS: drawSettings(canvas); break;
                case AUTHORS: drawAuthors(canvas); break;
                case PAUSED: drawPauseMenu(canvas); break;
                case GAME_OVER: drawGameOver(canvas); break;
            }
        }

        private void drawMainMenu(Canvas canvas) {
            canvas.drawColor(Color.rgb(20, 20, 30));
            titlePaint.setTextSize(150);
            canvas.drawText("DOOMCASTER", canvas.getWidth() / 2f, canvas.getHeight() / 3.5f, titlePaint);

            drawStyledButton(canvas, playButton, "PLAY", playButton.hashCode());
            drawStyledButton(canvas, settingsButton, "SETTINGS", settingsButton.hashCode());
            drawStyledButton(canvas, authorsButton, "AUTHORS", authorsButton.hashCode());
        }

        private void drawSettings(Canvas canvas) {
            drawGame(canvas);
            canvas.drawColor(Color.argb(200, 10, 10, 20));

            titlePaint.setTextSize(120);
            canvas.drawText("Settings", canvas.getWidth() / 2f, canvas.getHeight() / 5f, titlePaint);

            drawStyledButton(canvas, qualityButton, "QUALITY: " + graphicsQuality.name(), qualityButton.hashCode());

            textPaint.setTextSize(50);
            canvas.drawText("Field of View", canvas.getWidth() / 2f, fovDownButton.top - 20, textPaint);
            drawStyledButton(canvas, fovDownButton, "-", fovDownButton.hashCode());
            drawStyledButton(canvas, fovUpButton, "+", fovUpButton.hashCode());
            textPaint.setTextSize(70);
            canvas.drawText(String.format(Locale.US, "%.0f", fieldOfView), canvas.getWidth() / 2f, fovDownButton.centerY() + 25, textPaint);

            textPaint.setTextSize(50);
            canvas.drawText("Look Sensitivity", canvas.getWidth() / 2f, sensDownButton.top - 20, textPaint);
            drawStyledButton(canvas, sensDownButton, "-", sensDownButton.hashCode());
            drawStyledButton(canvas, sensUpButton, "+", sensUpButton.hashCode());
            textPaint.setTextSize(70);
            canvas.drawText(String.format(Locale.US, "%.3f", lookSensitivity * 1000), canvas.getWidth() / 2f, sensDownButton.centerY() + 25, textPaint);

            drawStyledButton(canvas, backButton, "BACK", backButton.hashCode());
        }

        private void drawAuthors(Canvas canvas) {
            canvas.drawColor(Color.rgb(20, 20, 30));
            titlePaint.setTextSize(120);
            canvas.drawText("Authors", canvas.getWidth() / 2f, canvas.getHeight() / 4f, titlePaint);
            textPaint.setTextSize(70);
            canvas.drawText("Cayman", canvas.getWidth() / 2f, canvas.getHeight() / 2f, textPaint);
            drawStyledButton(canvas, backButton, "BACK", backButton.hashCode());
        }

        private void drawGameOver(Canvas canvas) {
            canvas.drawColor(Color.argb(200, 100, 20, 20));
            titlePaint.setTextSize(150);
            canvas.drawText("YOU DIED", canvas.getWidth() / 2f, canvas.getHeight() / 3f, titlePaint);

            textPaint.setTextSize(70);
            canvas.drawText("Score: " + score, canvas.getWidth() / 2f, canvas.getHeight() / 2f, textPaint);
            canvas.drawText("Best: " + bestScore, canvas.getWidth() / 2f, canvas.getHeight() / 2f + 80, textPaint);

            textPaint.setTextSize(40);
            canvas.drawText("Tap to return to Menu", canvas.getWidth() / 2f, canvas.getHeight() - 200, textPaint);
        }

        private void drawPauseMenu(Canvas canvas) {
            canvas.drawColor(Color.argb(180, 10, 10, 20));
            titlePaint.setTextSize(150);
            canvas.drawText("PAUSED", canvas.getWidth() / 2f, canvas.getHeight() / 3f, titlePaint);

            drawStyledButton(canvas, resumeButton, "RESUME", resumeButton.hashCode());
            drawStyledButton(canvas, quitButton, "QUIT TO MENU", quitButton.hashCode());
        }

        private void drawGame(Canvas canvas) {
            int screenWidth = getWidth();
            int screenHeight = getHeight();
            paint.setColor(Color.rgb(40, 40, 40));
            canvas.drawRect(0, 0, screenWidth, screenHeight / 2.0f, paint);
            paint.setColor(Color.rgb(80, 80, 80));
            canvas.drawRect(0, screenHeight / 2.0f, screenWidth, screenHeight, paint);

            int rayStep = graphicsQuality == QualityLevel.LOW ? 4 : (graphicsQuality == QualityLevel.MEDIUM ? 2 : 1);

            double fovRadians = Math.toRadians(fieldOfView / 2.0);
            double playerDirX = Math.cos(playerAngle);
            double playerDirY = Math.sin(playerAngle);
            double planeX = -playerDirY * Math.tan(fovRadians);
            double planeY = playerDirX * Math.tan(fovRadians);


            for (int x = 0; x < screenWidth; x += rayStep) {
                double cameraX = 2.0 * x / screenWidth - 1.0;
                double rayDirX = playerDirX + planeX * cameraX;
                double rayDirY = playerDirY + planeY * cameraX;

                int mapX = (int)playerPos.x;
                int mapY = (int)playerPos.y;

                double sideDistX, sideDistY;
                double deltaDistX = (rayDirX == 0) ? 1e30 : Math.abs(1 / rayDirX);
                double deltaDistY = (rayDirY == 0) ? 1e30 : Math.abs(1 / rayDirY);
                double perpWallDist;

                int stepX, stepY;
                int hit = 0;
                int side = 0;

                if (rayDirX < 0) {
                    stepX = -1;
                    sideDistX = (playerPos.x - mapX) * deltaDistX;
                } else {
                    stepX = 1;
                    sideDistX = (mapX + 1.0 - playerPos.x) * deltaDistX;
                }
                if (rayDirY < 0) {
                    stepY = -1;
                    sideDistY = (playerPos.y - mapY) * deltaDistY;
                } else {
                    stepY = 1;
                    sideDistY = (mapY + 1.0 - playerPos.y) * deltaDistY;
                }

                while (hit == 0) {
                    if (sideDistX < sideDistY) {
                        sideDistX += deltaDistX;
                        mapX += stepX;
                        side = 0;
                    } else {
                        sideDistY += deltaDistY;
                        mapY += stepY;
                        side = 1;
                    }
                    if (mapX < 0 || mapX >= MAP_SIZE || mapY < 0 || mapY >= MAP_SIZE) {
                        hit = 1;
                        perpWallDist = 1000;
                    } else if (worldMap[mapY][mapX] > 0) {
                        hit = 1;
                    }
                }

                if (side == 0) perpWallDist = (sideDistX - deltaDistX);
                else          perpWallDist = (sideDistY - deltaDistY);

                if (perpWallDist < 0.01) perpWallDist = 0.01;

                depthBuffer[x] = perpWallDist;
                for(int i = 1; i < rayStep && x + i < screenWidth; i++) {
                    depthBuffer[x+i] = perpWallDist;
                }

                int lineHeight = (int)(screenHeight / perpWallDist);
                int drawStart = -lineHeight / 2 + screenHeight / 2;
                if(drawStart < 0) drawStart = 0;
                int drawEnd = lineHeight / 2 + screenHeight / 2;
                if(drawEnd >= screenHeight) drawEnd = screenHeight - 1;

                int textureID = worldMap[mapY][mapX];

                double wallX;
                if (side == 0) wallX = playerPos.y + perpWallDist * rayDirY;
                else           wallX = playerPos.x + perpWallDist * rayDirX;
                wallX -= Math.floor(wallX);

                drawWallColumn(canvas, x, drawStart, drawEnd, rayStep, textureID, side, wallX, perpWallDist, lineHeight);
            }
            drawSprites(canvas);
            drawGameUI(canvas);
            drawVisibleControls(canvas);
        }

        private void drawWallColumn(Canvas canvas, int x, int drawStart, int drawEnd, int rayStep, int textureID, int side, double wallX, double distance, int lineHeight) {
            if (textureID < 0 || textureID >= textures.size()) textureID = 0;
            Texture texture = textures.get(textureID);

            switch(graphicsQuality) {
                case HIGH:
                    int texX = (int)(wallX * texture.width);
                    texX = Math.max(0, Math.min(texture.width - 1, texX));

                    for (int y = drawStart; y < drawEnd; y++) {
                        int d = y * 256 - getHeight() * 128 + lineHeight * 128;
                        int texY = ((d * texture.height) / lineHeight) / 256;
                        texY = Math.max(0, Math.min(texture.height - 1, texY));
                        int color = texture.getPixel(texX, texY);
                        int shadedColor = applyShading(color, distance, side);
                        paint.setColor(shadedColor);
                        canvas.drawRect(x, y, x + rayStep, y + 1, paint);
                    }
                    break;
                case MEDIUM:
                    int texX_med = (int)(wallX * texture.width);
                    int texY_med = texture.height / 2;
                    int color_med = texture.getPixel(texX_med, texY_med);
                    int shadedColor_med = applyShading(color_med, distance, side);
                    paint.setColor(shadedColor_med);
                    canvas.drawRect(x, drawStart, x + rayStep, drawEnd, paint);
                    break;
                case LOW:
                    int color_low = texture.fallbackColor;
                    int shadedColor_low = applyShading(color_low, distance, side);
                    paint.setColor(shadedColor_low);
                    canvas.drawRect(x, drawStart, x + rayStep, drawEnd, paint);
                    break;
            }
        }


        private int applyShading(int color, double distance, int side) {
            float flashIntensity = (shootTimer > 0) ? (float)Math.max(0, 1.0 - distance / 8.0) * (shootTimer / (float)WEAPON_COOLDOWN_FRAMES) : 0;
            int r = Color.red(color); int g = Color.green(color); int b = Color.blue(color);
            r = Math.min(255, (int)(r + 255 * flashIntensity));
            g = Math.min(255, (int)(g + 200 * flashIntensity));
            b = Math.min(255, (int)(b + 150 * flashIntensity));

            double maxDist = 20.0;
            double shade = Math.max(0, 1.0 - (distance / maxDist));
            r *= shade; g *= shade; b *= shade;

            if (side == 1) { r *= 0.7; g *= 0.7; b *= 0.7; }

            return Color.rgb(r, g, b);
        }

        private void startGame() {
            level = 1;
            score = 0;
            playerHealth = 100;
            generateLevel();
            currentState = GameState.PLAYING;
        }

        private void generateLevel() {
            worldMap = new int[MAP_SIZE][MAP_SIZE];
            sprites.clear();
            spritesToAdd.clear();
            for (int y = 0; y < MAP_SIZE; y++) {
                for (int x = 0; x < MAP_SIZE; x++) {
                    worldMap[y][x] = 1;
                }
            }

            boolean[][] visited = new boolean[MAP_SIZE][MAP_SIZE];
            ArrayList<PointF> stack = new ArrayList<>();
            PointF current = new PointF(1, 1);
            visited[(int)current.y][(int)current.x] = true;
            worldMap[(int)current.y][(int)current.x] = 0;
            stack.add(current);

            while(!stack.isEmpty()) {
                current = stack.remove(stack.size() - 1);
                ArrayList<PointF> neighbors = new ArrayList<>();
                int cx = (int)current.x; int cy = (int)current.y;
                if (cx > 1 && !visited[cy][cx - 2]) neighbors.add(new PointF(cx - 2, cy));
                if (cx < MAP_SIZE - 2 && !visited[cy][cx + 2]) neighbors.add(new PointF(cx + 2, cy));
                if (cy > 1 && !visited[cy - 2][cx]) neighbors.add(new PointF(cx, cy - 2));
                if (cy < MAP_SIZE - 2 && !visited[cy + 2][cx]) neighbors.add(new PointF(cx, cy + 2));

                if (!neighbors.isEmpty()) {
                    stack.add(current);
                    PointF next = neighbors.get(random.nextInt(neighbors.size()));
                    int nx = (int)next.x; int ny = (int)next.y;
                    worldMap[(ny + cy) / 2][(nx + cx) / 2] = 0;
                    worldMap[ny][nx] = 0;
                    visited[ny][nx] = true;
                    stack.add(next);
                }
            }

            for (int y = 1; y < MAP_SIZE - 1; y++) {
                for (int x = 1; x < MAP_SIZE - 1; x++) {
                    if (worldMap[y][x] == 0) { // If it's an empty floor space
                        // --- FEATURE: Medkit --- Spawn enemies and medkits
                        if (random.nextFloat() < 0.05) { // 5% chance for an enemy
                            sprites.add(new Enemy(x + 0.5f, y + 0.5f));
                        } else if (random.nextFloat() < 0.02) { // 2% chance for a medkit
                            sprites.add(new Medkit(x + 0.5f, y + 0.5f));
                        }
                    } else if (worldMap[y][x] == 1) { // If it's a generic wall
                        worldMap[y][x] = random.nextInt(textures.size() - 1) + 1;
                    }
                }
            }

            playerPos = new PointF(1.5f, 1.5f);
            playerAngle = 0;

            int exitX, exitY;
            do {
                exitX = random.nextInt(MAP_SIZE - 2) + 1;
                exitY = random.nextInt(MAP_SIZE - 2) + 1;
            } while(worldMap[exitY][exitX] != 0 || (Math.abs(exitX - playerPos.x) + Math.abs(exitY - playerPos.y)) < MAP_SIZE / 2.0);
            sprites.add(new Portal(exitX + 0.5f, exitY + 0.5f));
        }

        private void handleCollisionAndMove(float newX, float newY) {
            int mapX = (int)newX; int mapY = (int)newY;
            if (mapX >= 0 && mapX < MAP_SIZE && mapY >= 0 && mapY < MAP_SIZE && worldMap[mapY][mapX] == 0) {
                playerPos.set(newX, newY);
            }
        }

        private void playerShoot() {
            shootTimer = WEAPON_COOLDOWN_FRAMES;

            double eyeX = Math.cos(playerAngle); double eyeY = Math.sin(playerAngle);
            for (double d = 0; d < 20; d += 0.1) {
                double testX = playerPos.x + eyeX * d; double testY = playerPos.y + eyeY * d;

                // --- BUG FIX: Add bounds check to prevent ArrayOutOfBoundsException ---
                int mapX = (int)testX;
                int mapY = (int)testY;
                if (mapX < 0 || mapX >= MAP_SIZE || mapY < 0 || mapY >= MAP_SIZE) {
                    break; // Ray has gone out of bounds
                }

                if (worldMap[mapY][mapX] > 0) break; // Use checked coordinates for wall collision

                for (Sprite s : sprites) {
                    if (s instanceof Enemy && s.isAlive && Math.hypot(s.x - testX, s.y - testY) < 0.5) {
                        ((Enemy) s).takeDamage(50);
                        score += 10;
                        return; // Hit an enemy, stop the ray
                    }
                }
            }
        }

        public void takeDamage(int amount) {
            playerHealth -= amount;
            if (playerHealth <= 0) {
                playerHealth = 0;
                currentState = GameState.GAME_OVER;
                if (score > bestScore) { bestScore = score; saveBestScore(); }
            }
        }

        // --- FEATURE: Medkit --- Method to heal the player ---
        public void playerHeal(int amount) {
            playerHealth += amount;
            if (playerHealth > 100) {
                playerHealth = 100;
            }
        }

        private void drawSprites(Canvas canvas) {
            Collections.sort(sprites, (s1, s2) -> Double.compare(s2.distToPlayer, s1.distToPlayer));

            final double dirX = Math.cos(playerAngle);
            final double dirY = Math.sin(playerAngle);
            final double planeX = -dirY * Math.tan(Math.toRadians(fieldOfView / 2.0));
            final double planeY = dirX * Math.tan(Math.toRadians(fieldOfView / 2.0));

            for (Sprite s : sprites) {
                double spriteWorldX = s.x - playerPos.x;
                double spriteWorldY = s.y - playerPos.y;
                double invDet = 1.0 / (planeX * dirY - dirX * planeY);
                double transformX = invDet * (dirY * spriteWorldX - dirX * spriteWorldY);
                double transformY = invDet * (-planeY * spriteWorldX + planeX * spriteWorldY);

                if (transformY > 0.1) {
                    int screenWidth = getWidth();
                    int screenHeight = getHeight();
                    int spriteScreenXCenter = (int) (screenWidth / 2.0 * (1.0 + transformX / transformY));

                    int spriteHeight = Math.abs((int) ((screenHeight / transformY) * s.scale));
                    int spriteWidth = spriteHeight;

                    float drawStartX = spriteScreenXCenter - spriteWidth / 2.0f;
                    float drawStartY = -spriteHeight / 2.0f + screenHeight / 2.0f;

                    reusableSpriteRect.set(drawStartX, drawStartY, drawStartX + spriteWidth, drawStartY + spriteHeight);
                    double correctedDist = transformY;
                    s.draw(canvas, reusableSpriteRect, depthBuffer, correctedDist);
                }
            }
        }


        private void drawGameUI(Canvas canvas) {
            // Health bar background
            paint.setColor(Color.rgb(80, 0, 0));
            canvas.drawRect(20, 20, 20 + 200, 60, paint);
            // Health bar foreground
            paint.setColor(Color.RED);
            canvas.drawRect(20, 20, 20 + (playerHealth * 2), 60, paint);
            // Health text
            textPaint.setTextSize(35);
            textPaint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("" + playerHealth, 25, 52, textPaint);

            // Score and Level text
            paint.setColor(Color.WHITE);
            paint.setTextSize(50);
            paint.setTextAlign(Paint.Align.LEFT);
            canvas.drawText("Score: " + score, 20, 120, paint);
            canvas.drawText("Best: " + bestScore, 20, 180, paint);
            canvas.drawText("Lvl: " + level, 20, 240, paint);

            // Gun
            int gunWidth = getWidth() / 4; int gunHeight = getHeight() / 3;
            paint.setColor(Color.DKGRAY);
            canvas.drawRect(getWidth() / 2f - gunWidth / 4f, getHeight() - gunHeight, getWidth() / 2f + gunWidth / 4f, getHeight(), paint);

            // Pause Icon
            uiPaint.setColor(Color.argb(150, 255, 255, 255));
            float barWidth = pauseButton.width() / 5;
            canvas.drawRect(pauseButton.left + barWidth, pauseButton.top, pauseButton.left + barWidth * 2, pauseButton.bottom, uiPaint);
            canvas.drawRect(pauseButton.right - barWidth * 2, pauseButton.top, pauseButton.right - barWidth, pauseButton.bottom, uiPaint);
        }

        private void drawVisibleControls(Canvas canvas) {
            paint.setColor(Color.argb(100, 200, 200, 200));
            canvas.drawCircle(joystickBase.x, joystickBase.y, joystickRadius, paint);
            paint.setColor(Color.argb(150, 255, 255, 255));
            canvas.drawCircle(joystickKnob.x, joystickKnob.y, joystickRadius / 2, paint);

            drawButtonWithText(canvas, shootButton, "SHOOT");
        }

        private void drawStyledButton(Canvas canvas, RectF bounds, String text, int buttonId) {
            if (pressedButton == buttonId) {
                uiPaint.setColor(Color.rgb(60, 60, 80));
            } else {
                uiPaint.setColor(Color.rgb(40, 40, 50));
            }
            uiPaint.setStyle(Paint.Style.FILL);
            canvas.drawRoundRect(bounds, 20, 20, uiPaint);

            uiPaint.setColor(Color.rgb(120, 120, 150));
            uiPaint.setStyle(Paint.Style.STROKE);
            uiPaint.setStrokeWidth(4);
            canvas.drawRoundRect(bounds, 20, 20, uiPaint);

            textPaint.setTextSize(bounds.height() * 0.4f);
            textPaint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, bounds.centerX(), bounds.centerY() + (textPaint.getTextSize() / 3), textPaint);
        }

        private void drawButtonWithText(Canvas canvas, RectF bounds, String text) {
            paint.setColor(Color.argb(100, 200, 200, 200));
            canvas.drawRect(bounds, paint);
            paint.setColor(Color.WHITE);
            paint.setTextSize(40);
            paint.setTextAlign(Paint.Align.CENTER);
            canvas.drawText(text, bounds.centerX(), bounds.centerY() + 15, paint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();

            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                pressedButton = 0;
            }

            if (currentState == GameState.PLAYING) {
                handleGameTouch(event);
            } else {
                if (action == MotionEvent.ACTION_DOWN) {
                    float x = event.getX();
                    float y = event.getY();
                    switch (currentState) {
                        case MAIN_MENU: handleMenuTouch(x, y); break;
                        case SETTINGS: handleSettingsTouch(x, y); break;
                        case AUTHORS: handleAuthorsTouch(x, y); break;
                        case PAUSED: handlePauseTouch(x, y); break;
                        case GAME_OVER: currentState = GameState.MAIN_MENU; break;
                    }
                }
            }
            return true;
        }

        private void handleMenuTouch(float x, float y) {
            if (playButton.contains(x, y)) { pressedButton = playButton.hashCode(); startGame(); }
            if (settingsButton.contains(x, y)) { pressedButton = settingsButton.hashCode(); currentState = GameState.SETTINGS; }
            if (authorsButton.contains(x, y)) { pressedButton = authorsButton.hashCode(); currentState = GameState.AUTHORS; }
        }

        private void handleSettingsTouch(float x, float y) {
            if (backButton.contains(x, y)) { pressedButton = backButton.hashCode(); currentState = GameState.MAIN_MENU; }

            if (qualityButton.contains(x,y)) {
                pressedButton = qualityButton.hashCode();
                int nextOrdinal = (graphicsQuality.ordinal() + 1) % QualityLevel.values().length;
                graphicsQuality = QualityLevel.values()[nextOrdinal];
            }

            if (fovDownButton.contains(x,y)) { pressedButton = fovDownButton.hashCode(); fieldOfView = Math.max(40, fieldOfView - 1); }
            if (fovUpButton.contains(x,y)) { pressedButton = fovUpButton.hashCode(); fieldOfView = Math.min(120, fieldOfView + 1); }
            if (sensDownButton.contains(x,y)) { pressedButton = sensDownButton.hashCode(); lookSensitivity = Math.max(0.001f, lookSensitivity - 0.0005f); }
            if (sensUpButton.contains(x,y)) { pressedButton = sensUpButton.hashCode(); lookSensitivity = Math.min(0.01f, lookSensitivity + 0.0005f); }
        }

        private void handleAuthorsTouch(float x, float y) {
            if (backButton.contains(x, y)) { pressedButton = backButton.hashCode(); currentState = GameState.MAIN_MENU; }
        }

        private void handlePauseTouch(float x, float y) {
            if (resumeButton.contains(x,y)) { pressedButton = resumeButton.hashCode(); currentState = GameState.PLAYING; }
            if (quitButton.contains(x,y)) { pressedButton = quitButton.hashCode(); currentState = GameState.MAIN_MENU; }
        }

        private void handleGameTouch(MotionEvent event) {
            int action = event.getActionMasked();
            int pointerIndex = event.getActionIndex();
            int pointerId = event.getPointerId(pointerIndex);

            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_POINTER_DOWN) {
                float x = event.getX(pointerIndex);
                float y = event.getY(pointerIndex);
                if (pauseButton.contains(x,y)) {
                    currentState = GameState.PAUSED;
                    return;
                }
            }

            switch (action) {
                case MotionEvent.ACTION_DOWN:
                case MotionEvent.ACTION_POINTER_DOWN: {
                    float x = event.getX(pointerIndex);
                    float y = event.getY(pointerIndex);

                    if (shootButton.contains(x, y)) {
                        playerShoot();
                    } else if (x < getWidth() / 2f && joystickPointerId == -1) {
                        joystickPointerId = pointerId;
                        joystickBase.set(x, y);
                        joystickKnob.set(x, y);
                    } else if (x >= getWidth() / 2f && lookPointerId == -1) {
                        lookPointerId = pointerId;
                        lastLookX = x;
                    }
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    for (int i = 0; i < event.getPointerCount(); i++) {
                        int id = event.getPointerId(i);
                        float px = event.getX(i);
                        float py = event.getY(i);

                        if (id == joystickPointerId) {
                            float dx = px - joystickBase.x;
                            float dy = py - joystickBase.y;
                            double dist = Math.hypot(dx, dy);
                            if (dist > joystickRadius) {
                                joystickKnob.set(joystickBase.x + dx / (float)dist * joystickRadius, joystickBase.y + dy / (float)dist * joystickRadius);
                            } else {
                                joystickKnob.set(px, py);
                            }
                        } else if (id == lookPointerId) {
                            float dx = px - lastLookX;
                            playerAngle += dx * lookSensitivity;
                            lastLookX = px;
                        }
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_POINTER_UP: {
                    if (pointerId == joystickPointerId) {
                        joystickPointerId = -1;
                        joystickBase.set(joystickDefaultPos);
                        joystickKnob.set(joystickDefaultPos);
                    } else if (pointerId == lookPointerId) {
                        lookPointerId = -1;
                    }
                    break;
                }
            }

            if (joystickPointerId != -1) {
                float dx = joystickKnob.x - joystickBase.x;
                float dy = joystickKnob.y - joystickBase.y;
                moveVector.x = dx / joystickRadius;
                moveVector.y = -dy / joystickRadius;
            } else {
                moveVector.set(0, 0);
            }
        }

        private void saveBestScore() {
            SharedPreferences prefs = getContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE);
            SharedPreferences.Editor editor = prefs.edit();
            editor.putInt("bestScore", bestScore);
            editor.apply();
        }

        private void loadBestScore() {
            SharedPreferences prefs = getContext().getSharedPreferences("GamePrefs", Context.MODE_PRIVATE);
            bestScore = prefs.getInt("bestScore", 0);
        }

        public void resume() {
            if (currentState != GameState.PAUSED) {
                isPlaying = true;
                handler.post(gameLoop);
            }
        }
        public void pause() {
            isPlaying = false;
        }

        private void loadTextures() {
            textures.clear();
            textures.add(new Texture(64,64, Color.MAGENTA));

            Texture bricks = new Texture(64, 64, Color.rgb(150, 100, 100));
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    int c = ((x % 32 < 2) || (y % 32 < 2)) ? Color.rgb(80, 80, 80) : Color.rgb(150, 100, 100);
                    if ((y/32)%2==0) bricks.setPixel(x,y,c); else bricks.setPixel((x+16)%64, y, c);
                }
            }
            textures.add(bricks);

            Texture stone = new Texture(64, 64, Color.GRAY);
            for (int i = 0; i < 64*64; i++) {
                int c = 100 + random.nextInt(30);
                stone.pixels[i] = Color.rgb(c, c, c);
            }
            textures.add(stone);

            Texture wood = new Texture(64, 64, Color.rgb(120, 60, 30));
            for (int x = 0; x < 64; x++) {
                for (int y = 0; y < 64; y++) {
                    int c = 100 + (y % 8) * 5 + random.nextInt(10);
                    if (x % 32 < 2) c = 80;
                    wood.setPixel(x, y, Color.rgb(c, c/2, c/3));
                }
            }
            textures.add(wood);
        }

        class Texture {
            public final int[] pixels;
            public final int width, height;
            public final int fallbackColor;

            public Texture(int width, int height, int fallbackColor) {
                this.width = width; this.height = height;
                pixels = new int[width * height];
                this.fallbackColor = fallbackColor;
            }
            public void setPixel(int x, int y, int color) { pixels[y * width + x] = color; }
            public int getPixel(int x, int y) { return pixels[(y & (height - 1)) * width + (x & (width - 1))]; }
        }

        //==========================================================================================
        // --- NESTED CLASSES START ---
        //==========================================================================================

        abstract class Sprite {
            public float x, y;
            public double distToPlayer = 0;
            public boolean isAlive = true;
            public float scale = 1.0f;
            public abstract void update();
            public abstract void draw(Canvas canvas, RectF screenRect, double[] depthBuffer, double correctedDist);
        }

        class Enemy extends Sprite {
            private int health = 100;
            private long lastShotTime = 0;
            private static final long SHOT_COOLDOWN_MS = 2000;
            private static final float LINE_OF_SIGHT_RANGE = 10.0f;
            private static final float LINE_OF_SIGHT_STEP = 0.2f;


            public Enemy(float x, float y) {
                this.x = x;
                this.y = y;
            }

            @Override
            public void update() {
                if (distToPlayer < LINE_OF_SIGHT_RANGE && hasLineOfSight() && System.currentTimeMillis() - lastShotTime > SHOT_COOLDOWN_MS) {
                    lastShotTime = System.currentTimeMillis();
                    double angleToPlayer = Math.atan2(playerPos.y - y, playerPos.x - x);
                    float startX = x + (float)Math.cos(angleToPlayer) * 0.5f;
                    float startY = y + (float)Math.sin(angleToPlayer) * 0.5f;
                    spritesToAdd.add(new Rocket(startX, startY, playerPos));
                }
            }

            private boolean hasLineOfSight() {
                double angleToPlayer = Math.atan2(playerPos.y - y, playerPos.x - x);
                double rayDirX = Math.cos(angleToPlayer);
                double rayDirY = Math.sin(angleToPlayer);

                for (double d = 0; d < distToPlayer; d += LINE_OF_SIGHT_STEP) {
                    float testX = (float)(x + rayDirX * d);
                    float testY = (float)(y + rayDirY * d);

                    // --- BUG FIX: Add bounds check to prevent ArrayOutOfBoundsException ---
                    int mapX = (int)testX;
                    int mapY = (int)testY;
                    if (mapX < 0 || mapX >= MAP_SIZE || mapY < 0 || mapY >= MAP_SIZE) {
                        return false; // Path is blocked by going out of map
                    }

                    if (worldMap[mapY][mapX] > 0) { // Use checked coordinates
                        return false;
                    }
                }
                return true;
            }

            @Override
            public void draw(Canvas canvas, RectF screenRect, double[] depthBuffer, double correctedDist) {
                for (int i = (int)screenRect.left; i < (int)screenRect.right; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        paint.setColor(Color.rgb(200, 0, 0));
                        canvas.drawRect(i, screenRect.top, i + 1, screenRect.bottom, paint);
                    }
                }

                float healthWidth = screenRect.width() * (health / 100f);
                reusableHealthBarRect.set(screenRect.left, screenRect.top - 20, screenRect.left + healthWidth, screenRect.top - 10);

                for (int i = (int)reusableHealthBarRect.left; i < (int)reusableHealthBarRect.right; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        paint.setColor(Color.GREEN);
                        canvas.drawRect(i, reusableHealthBarRect.top, i+1, reusableHealthBarRect.bottom, paint);
                    }
                }
            }

            public void takeDamage(int amount) {
                health -= amount;
                if (health <= 0) {
                    isAlive = false;
                    score += 50;
                }
            }
        }

        class Rocket extends Sprite {
            private float velX, velY;
            private static final float ROCKET_SPEED = 0.08f;
            private static final float ROCKET_SCALE = 0.3f;
            private static final float ROCKET_COLLISION_RADIUS = 0.5f;
            private static final int ROCKET_DAMAGE = 10;

            public Rocket(float startX, float startY, PointF target) {
                this.x = startX;
                this.y = startY;
                this.scale = ROCKET_SCALE;
                double angle = Math.atan2(target.y - y, target.x - x);
                this.velX = (float) (Math.cos(angle) * ROCKET_SPEED);
                this.velY = (float) (Math.sin(angle) * ROCKET_SPEED);
            }

            @Override
            public void update() {
                x += velX;
                y += velY;
                if (Math.hypot(x - playerPos.x, y - playerPos.y) < ROCKET_COLLISION_RADIUS) {
                    isAlive = false;
                    GameView.this.takeDamage(ROCKET_DAMAGE);
                }
                int mapX = (int)this.x;
                int mapY = (int)this.y;
                if (mapX >= 0 && mapX < MAP_SIZE && mapY >= 0 && mapY < MAP_SIZE) {
                    if (worldMap[mapY][mapX] > 0) {
                        isAlive = false;
                    }
                } else {
                    isAlive = false;
                }
            }

            @Override
            public void draw(Canvas canvas, RectF screenRect, double[] depthBuffer, double correctedDist) {
                paint.setColor(Color.YELLOW);
                for (int i = (int)screenRect.left; i < (int)screenRect.right; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        canvas.drawRect(i, screenRect.top, i + 1, screenRect.bottom, paint);
                    }
                }
            }
        }

        class Portal extends Sprite {
            private static final float PORTAL_ACTIVATION_DISTANCE = 0.8f;

            public Portal(float x, float y) { this.x = x; this.y = y; }
            @Override
            public void update() {
                if(distToPlayer < PORTAL_ACTIVATION_DISTANCE) {
                    level++;
                    score += 100;
                    generateLevel();
                }
            }
            @Override
            public void draw(Canvas canvas, RectF screenRect, double[] depthBuffer, double correctedDist) {
                for (int i = (int)screenRect.left; i < (int)screenRect.right; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        int c = (int)(Math.sin(System.currentTimeMillis() / 200.0) * 127 + 128);
                        paint.setColor(Color.rgb(c, 0, c));
                        canvas.drawRect(i, screenRect.top, i + 1, screenRect.bottom, paint);
                    }
                }
            }
        }

        // --- FEATURE: Medkit --- New sprite class for health packs ---
        class Medkit extends Sprite {
            private static final float ACTIVATION_DISTANCE = 0.6f;
            private static final int HEAL_AMOUNT = 25;

            public Medkit(float x, float y) {
                this.x = x;
                this.y = y;
                this.scale = 0.4f; // Make medkits a bit smaller than a full wall tile
            }

            @Override
            public void update() {
                if (distToPlayer < ACTIVATION_DISTANCE) {
                    GameView.this.playerHeal(HEAL_AMOUNT);
                    isAlive = false; // Medkit is used up
                }
            }

            @Override
            public void draw(Canvas canvas, RectF screenRect, double[] depthBuffer, double correctedDist) {
                // Draw Green Background
                paint.setColor(Color.rgb(0, 150, 0));
                for (int i = (int) screenRect.left; i < (int) screenRect.right; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        canvas.drawRect(i, screenRect.top, i + 1, screenRect.bottom, paint);
                    }
                }

                // Draw White Cross on top
                paint.setColor(Color.WHITE);
                float crossThickness = Math.max(2f, screenRect.width() / 4f);

                // Horizontal bar
                float horizTop = screenRect.centerY() - crossThickness / 2;
                float horizBottom = screenRect.centerY() + crossThickness / 2;
                for (int i = (int) screenRect.left; i < (int) screenRect.right; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        canvas.drawRect(i, horizTop, i + 1, horizBottom, paint);
                    }
                }

                // Vertical bar
                float vertLeft = screenRect.centerX() - crossThickness / 2;
                float vertRight = screenRect.centerX() + crossThickness / 2;
                for (int i = (int) vertLeft; i < (int) vertRight; i++) {
                    if (i >= 0 && i < depthBuffer.length && depthBuffer[i] > correctedDist) {
                        canvas.drawRect(i, screenRect.top, i + 1, screenRect.bottom, paint);
                    }
                }
            }
        }

    } // --- GAMEVIEW CLASS END ---
} // --- MAINACTIVITY CLASS END ---