# DOOMCASTER: A Retro Raycasting FPS for Android

*(A placeholder for a gameplay screenshot showing the 3D maze, an enemy, and the on-screen controls.)*

**DOOMCASTER** is a classic pseudo-3D first-person shooter built from the ground up for the Android platform. Inspired by 90s legends like Wolfenstein 3D and DOOM, this game features a custom-built raycasting engine that renders a 3D world on a 2D map. Navigate procedurally generated mazes, fight off hostile entities, manage your health, and dive through portals to conquer level after level in a quest for the highest score.

-----

## Table of Contents

* [About The Game](https://www.google.com/search?q=%23about-the-game)
* [Features](https://www.google.com/search?q=%23features)
* [Gameplay & Controls](https://www.google.com/search?q=%23gameplay--controls)
* [Technical Deep Dive](https://www.google.com/search?q=%23technical-deep-dive)
* [Getting Started](https://www.google.com/search?q=%23getting-started)
* [Code Structure](https://www.google.com/search?q=%23code-structure)
* [Future Improvements](https://www.google.com/search?q=%23future-improvements)
* [Author](https://www.google.com/search?q=%23author)
* [License](https://www.google.com/search?q=%23license)

-----

## About The Game

This project was developed as a comprehensive exercise in game engine fundamentals, Android development, and software architecture. It avoids using pre-built game engines like Unity or Unreal to instead focus on the core principles of 3D graphics simulation.

The player is dropped into a maze with a simple objective: find the exit portal. However, the maze is patrolled by enemies who fire deadly projectiles. The player must use their own weapon to eliminate threats while picking up medkits to survive. Each new level is a completely new, randomly generated maze, offering endless replayability. The game tracks your current score and saves your all-time best score, encouraging you to beat your own records.

## Features

* **Custom-Built Raycasting Engine:** A performant, from-scratch engine that renders a 3D perspective from a 2D tile map, complete with textured walls, lighting/shading based on distance, and sprite rendering.
* **Procedurally Generated Levels:** Using a randomized Depth-First Search algorithm, the game creates a new, unique, and solvable maze every time you enter a portal, ensuring no two playthroughs are the same.
* **Dynamic Enemy AI:** Enemies use line-of-sight checks to spot the player and fire projectiles, creating a dynamic and challenging combat experience.
* **Interactive Sprites:** The world is populated with multiple types of sprites:
    * **Enemies:** Hostile entities that shoot at the player.
    * **Rockets:** Projectiles fired by enemies that can damage the player.
    * **Medkits:** Health packs that restore 25 HP upon pickup.
    * **Portals:** The exit gate to the next level.
* **Immersive On-Screen Controls:** The UI is designed for intuitive mobile gameplay, featuring a virtual joystick for movement, a dedicated fire button, and swipe-to-look aiming.
* **Customizable Settings:** Players can adjust:
    * **Graphics Quality:** Switch between High, Medium, and Low settings to balance visual fidelity and performance.
    * **Field of View (FOV):** Customize the camera's field of view.
    * **Look Sensitivity:** Adjust the aiming sensitivity to your preference.
* **Persistent High Score:** The game saves your best score locally on your device, giving you a constant goal to strive for.

## Gameplay & Controls

* **Objective:** Survive the maze, eliminate enemies, and find the purple portal to advance to the next level and increase your score.
* **Controls:**
    * **Movement:** Use the virtual joystick on the left half of the screen to move forward, backward, and strafe left or right.
    * **Aiming:** Swipe horizontally on the right half of the screen to look left and right.
    * **Shooting:** Press the large "SHOOT" button on the bottom-right to fire your weapon.
    * **Pausing:** Tap the pause icon in the top-right corner to pause the game and access the pause menu.

## Technical Deep Dive

The core of DOOMCASTER is its rendering engine, which uses several classic techniques:

* **Raycasting:** For every vertical column of pixels on the screen, a single ray is cast out from the player's camera. The engine uses the Digital Differential Analysis (DDA) algorithm to efficiently calculate where this ray intersects with a wall on the 2D grid map.
* **Wall Rendering:** Once a wall hit is detected, the distance to that wall is calculated to determine the wall slice's height on the screen (creating the illusion of perspective). Based on the graphics settings, the appropriate wall texture is then scaled and drawn for that pixel column. Shading is applied to make distant walls appear darker.
* **Sprite Rendering & Depth Buffering:**
    1.  All sprites (enemies, items) are first transformed from world coordinates to screen coordinates relative to the player's position and angle.
    2.  They are sorted from farthest to nearest (a "Painter's Algorithm" approach).
    3.  During the wall rendering phase, the distance of the nearest wall for each screen column is stored in a **depth buffer** (or z-buffer).
    4.  When drawing sprites, each vertical column of the sprite is only drawn if its distance from the player is less than the distance stored in the depth buffer for that screen column. This ensures that sprites correctly appear in front of or behind walls, and are not drawn when they are occluded.

## Getting Started

To build and run this project yourself, you will need:

* Android Studio (latest version recommended)
* An Android device or emulator running Android 5.0 (Lollipop) or newer.

**Build Steps:**

1.  Clone this repository to your local machine.
2.  Open Android Studio and select "Open" or "Open an Existing Project".
3.  Navigate to the cloned repository folder and select it.
4.  Allow Android Studio to sync the Gradle files and download any dependencies.
5.  Once the sync is complete, you can build and run the app on your connected device or emulator by clicking the "Run" button (▶️).

## Code Structure

The entire game logic is encapsulated within the `MainActivity.java` file for simplicity. The key components are:

* **`MainActivity`:** The top-level Android Activity that hosts the game.
* **`GameView`:** A custom `View` that acts as the main game class. It contains the game loop, handles all rendering, manages game state (menus, playing, game over), and processes all touch input.
* **`GameState` (enum):** A simple but powerful state machine that dictates what is currently being updated and drawn (e.g., `MAIN_MENU`, `PLAYING`, `PAUSED`).
* **`Sprite` (abstract class):** The base class for all dynamic objects in the game world.
    * **`Enemy`:** A subclass of `Sprite` with health and AI for shooting at the player.
    * **`Rocket`:** A projectile sprite with velocity.
    * **`Portal`:** A sprite that triggers level progression.
    * **`Medkit`:** A pickup sprite that heals the player.
* **`Texture`:** A helper class that holds pixel data for wall textures, which are generated procedurally at startup.

## Future Improvements

This project provides a solid foundation that can be expanded in many ways:

* [ ] **Sound Engine:** Implement sound effects for shooting, enemy alerts, pickups, and background music.
* [ ] **More Weapons:** Add new weapon types for the player to use.
* [ ] **More Enemy Types:** Introduce enemies with different behaviors, health, and attack patterns.
* [ ] **Advanced Level Features:** Add interactive elements like locked doors, keys, and switches.
* [ ] **Texture Loading:** Modify the engine to load textures from image files (`.png`, `.jpg`) instead of generating them with code.
* [ ] **Improved HUD:** Enhance the Heads-Up Display with more information, such as an ammo count.

## Author

* **Cayman**
