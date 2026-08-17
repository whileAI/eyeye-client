<p align="center">
  <img src="https://raw.githubusercontent.com/whileAI/eyeye-client/master/imgs/logo.png" alt="EyEye Client logo" width="128">
</p>

<h1 align="center">EyEye Client</h1>

<p align="center">Free and open-source Fabric utility client for Minecraft.</p>

<p align="center">
  <a href="#installation">Installation</a> ·
  <a href="#building">Building</a> ·
  <a href="#credits">Credits</a> ·
  <a href="https://github.com/whileAI/eyeye-client/issues">Issues</a>
</p>

## About

EyEye Client is a fork of [Meteor Client](https://github.com/MeteorDevelopment/meteor-client), redesigned and extended with its own tools and interface.

## Highlights

- Fabric mod with an in-game Click GUI
- Embedded Baritone integration
- EyEye Chat for players on the same server
- Movement, render, world and automation modules
- Fully open source under GPL-3.0

## Installation

1. Install Fabric Loader for the Minecraft version supported by the release.
2. Download the latest EyEye Client JAR from [Releases](https://github.com/whileAI/eyeye-client/releases).
3. Place the JAR into your Minecraft instance `mods` folder.
4. Launch the game.

## EyEye Chat

Enable chat reception:

```text
;chat-status true
```

Send a message to EyEye users on the same multiplayer server:

```text
;chat Your message
```

Disable it at any time with `;chat-status false`.

## Building

```bash
./gradlew build
```

The built JAR is placed in `build/libs`.

## Contributing

Pull requests and issues are welcome. Keep changes focused, follow the existing code style, and do not commit IDE or system files.

## Credits

- [whileAI](https://github.com/whileAI) — development and design
- [vexarofc](https://github.com/vexarofc) — created ideas and helped bring them to life
- [ChatGPT / OpenAI](https://github.com/openai) — coding assistance and ideas
- [Meteor Client contributors](https://github.com/MeteorDevelopment/meteor-client/graphs/contributors) — original project

## License

EyEye Client is licensed under the [GNU General Public License v3.0](LICENSE).

Any modified or redistributed version that includes EyEye Client code must remain open source under GPL-3.0 and provide its source code.
