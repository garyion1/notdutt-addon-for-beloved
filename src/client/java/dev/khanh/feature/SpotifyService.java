package dev.khanh.feature;

import dev.khanh.KhanhClient;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import net.minecraft.client.MinecraftClient;

/**
 * Now-playing link to the Spotify desktop app on Windows, macOS and Linux.
 * <ul>
 *   <li>Windows: a small PowerShell helper reads Windows' media controls (the same source as the
 *   volume flyout): song, artist, position, length, album art, and it plays/pauses/skips. If those
 *   controls are unavailable it falls back to Spotify's window title.</li>
 *   <li>macOS: Spotify's AppleScript dictionary.</li>
 *   <li>Linux: {@code playerctl} (MPRIS).</li>
 * </ul>
 */
public final class SpotifyService {
    private static final String SEPARATOR = "\u001F";
    private static final String MAC_QUERY = """
        if application "Spotify" is not running then return "INACTIVE"
        tell application "Spotify"
            set sep to ASCII character 31
            set stateText to (player state as text)
            set trackName to name of current track
            set artistName to artist of current track
            set positionMillis to (round (player position * 1000))
            set durationMillis to duration of current track
            set artUrl to ""
            try
                set artUrl to artwork url of current track
            end try
            return stateText & sep & trackName & sep & artistName & sep & positionMillis & sep & durationMillis & sep & artUrl
        end tell
        """;

    /** Windows helper: prints one state line a second and runs commands read from stdin. */
    private static final String WINDOWS_HELPER = """
        $ErrorActionPreference = 'SilentlyContinue'
        try { [Console]::OutputEncoding = [System.Text.Encoding]::UTF8 } catch {}
        $sep = [string][char]31
        $art = Join-Path $env:TEMP 'khanh-spotify-art.img'
        $script:lastKey = ''
        $script:artVersion = 0
        $script:hasArt = $false
        $script:asTask = $null
        $script:mgr = $null

        function Await($op, [Type]$type) {
            $task = $script:asTask.MakeGenericMethod($type).Invoke($null, @($op))
            $null = $task.Wait(3000)
            return $task.Result
        }

        try {
            Add-Type -AssemblyName System.Runtime.WindowsRuntime
            $script:asTask = [System.WindowsRuntimeSystemExtensions].GetMethods() | Where-Object {
                $_.Name -eq 'AsTask' -and $_.GetParameters().Count -eq 1 -and $_.GetParameters()[0].ParameterType.Name -eq 'IAsyncOperation`1'
            } | Select-Object -First 1
            $null = [Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager, Windows.Media.Control, ContentType = WindowsRuntime]
            $null = [Windows.Storage.Streams.IRandomAccessStreamWithContentType, Windows.Storage.Streams, ContentType = WindowsRuntime]
            $script:mgr = Await ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager]::RequestAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionManager])
        } catch { $script:mgr = $null }

        function Get-SpotifySession {
            if ($null -eq $script:mgr) { return $null }
            try {
                if ($env:KHANH_ANY_MEDIA -eq '1') {
                    $current = $script:mgr.GetCurrentSession()
                    if ($null -ne $current) { return $current }
                }
                foreach ($s in $script:mgr.GetSessions()) {
                    if ($s.SourceAppUserModelId -like '*Spotify*') { return $s }
                }
            } catch {}
            return $null
        }

        function Send-MediaKey([byte]$vk) {
            try {
                if (-not ('KhanhClientKeys' -as [type])) {
                    Add-Type -TypeDefinition 'using System; using System.Runtime.InteropServices; public static class KhanhClientKeys { [DllImport("user32.dll")] public static extern void keybd_event(byte k, byte s, uint f, UIntPtr e); }'
                }
                [KhanhClientKeys]::keybd_event($vk, 0, 1, [UIntPtr]::Zero)
                [KhanhClientKeys]::keybd_event($vk, 0, 3, [UIntPtr]::Zero)
            } catch {}
        }

        function Save-Art($props) {
            $script:hasArt = $false
            try {
                if ($null -ne $props.Thumbnail) {
                    $rs = Await ($props.Thumbnail.OpenReadAsync()) ([Windows.Storage.Streams.IRandomAccessStreamWithContentType])
                    $in = [System.IO.WindowsRuntimeStreamExtensions]::AsStreamForRead($rs)
                    $out = [System.IO.File]::Create($art)
                    $in.CopyTo($out)
                    $out.Close(); $in.Close()
                    $script:artVersion++
                    $script:hasArt = $true
                }
            } catch { $script:hasArt = $false }
        }

        function Write-State {
            $line = $null
            $s = Get-SpotifySession
            if ($null -ne $s) {
                try {
                    $p = Await ($s.TryGetMediaPropertiesAsync()) ([Windows.Media.Control.GlobalSystemMediaTransportControlsSessionMediaProperties])
                    $info = $s.GetPlaybackInfo()
                    $tl = $s.GetTimelineProperties()
                    $playing = ([string]$info.PlaybackStatus) -eq 'Playing'
                    $pos = [long]$tl.Position.TotalMilliseconds
                    if ($playing -and $tl.LastUpdatedTime.Year -gt 2000) {
                        $pos += [long]([DateTimeOffset]::Now - $tl.LastUpdatedTime).TotalMilliseconds
                    }
                    $dur = [long]$tl.EndTime.TotalMilliseconds
                    $key = [string]$p.Title + '|' + [string]$p.Artist + '|' + [string]$p.AlbumTitle
                    if ($key -ne $script:lastKey) { $script:lastKey = $key; Save-Art $p }
                    $artField = ''
                    if ($script:hasArt) { $artField = 'file:' + $art + '#' + $script:artVersion }
                    $state = 'paused'
                    if ($playing) { $state = 'playing' }
                    $line = $state + $sep + [string]$p.Title + $sep + [string]$p.Artist + $sep + $pos + $sep + $dur + $sep + $artField
                } catch { $line = $null }
            }
            if ($null -eq $line) {
                $proc = Get-Process -Name Spotify | Where-Object { $_.MainWindowTitle } | Select-Object -First 1
                if ($null -eq $proc) {
                    $line = 'INACTIVE'
                } elseif ($proc.MainWindowTitle -match '^(.+?) - (.+)$') {
                    $line = 'playing' + $sep + $Matches[2] + $sep + $Matches[1] + $sep + '0' + $sep + '0' + $sep
                } else {
                    $line = 'OPEN'
                }
            }
            [Console]::Out.WriteLine($line)
            [Console]::Out.Flush()
        }

        # Console.In's async read blocks in Windows PowerShell; a StreamReader on stdin does not.
        $stdin = New-Object System.IO.StreamReader([Console]::OpenStandardInput())
        $pending = $stdin.ReadLineAsync()
        $next = [DateTime]::MinValue
        while ($true) {
            if ($pending.IsCompleted) {
                $cmd = $pending.Result
                if ($null -eq $cmd) { exit }
                $s = Get-SpotifySession
                $done = $false
                if ($null -ne $s) {
                    try {
                        switch ($cmd) {
                            'playpause' { $null = Await ($s.TryTogglePlayPauseAsync()) ([bool]); $done = $true }
                            'next' { $null = Await ($s.TrySkipNextAsync()) ([bool]); $done = $true }
                            'previous' { $null = Await ($s.TrySkipPreviousAsync()) ([bool]); $done = $true }
                        }
                    } catch { $done = $false }
                }
                if (-not $done) {
                    switch ($cmd) {
                        'playpause' { Send-MediaKey 0xB3 }
                        'next' { Send-MediaKey 0xB0 }
                        'previous' { Send-MediaKey 0xB1 }
                    }
                }
                $pending = $stdin.ReadLineAsync()
                Start-Sleep -Milliseconds 250
                $next = [DateTime]::MinValue
            }
            if ([DateTime]::UtcNow -ge $next) {
                Write-State
                $next = [DateTime]::UtcNow.AddMilliseconds(1000)
            }
            Start-Sleep -Milliseconds 60
        }
        """;

    private enum Platform { WINDOWS, MAC, LINUX, OTHER }

    private static final Platform PLATFORM = detect();
    private final AtomicBoolean querying = new AtomicBoolean();
    private volatile SpotifyState state = SpotifyState.IDLE;
    private int ticks;
    private int idleTicks;

    // Windows helper process.
    private volatile Process helper;
    private volatile BufferedWriter helperInput;
    private volatile long helperRetryAt;
    private volatile long lastHelperLine;
    private boolean shutdownHook;
    private volatile boolean helperAnyMedia;

    /** Media Player on: follow whatever is playing (any app), not just Spotify. */
    public static boolean anyMedia() {
        return KhanhFeatures.enabled("media_player");
    }

    /** The HUD card is shown by Music Player or Media Player. */
    public static boolean cardWanted() {
        return KhanhFeatures.enabled("spotify_hud") || KhanhFeatures.enabled("media_player");
    }

    public SpotifyState state() {
        if (KhanhFeatures.bool("spotify_hud", "demo", false)) {
            long duration = 204_000L;
            long position = (System.nanoTime() / 1_000_000L) % duration;
            return new SpotifyState(true, true, true, "Pink Horizons (Extended Night Drive Mix)", "Khanh Client Radio", position, duration, System.nanoTime(),
                "resource:" + KhanhClient.MOD_ID + ":textures/gui/khanh-logo.png");
        }
        return state;
    }

    /** Album art for the current song, loaded in the background; null until it is ready. */
    public SpotifyArt.Art art() {
        return SpotifyArt.current(state().artKey());
    }

    public void tick(MinecraftClient client) {
        boolean enabled = cardWanted();
        if (!enabled) {
            // Let the helper go after a short while so turning the HUD off frees it.
            if (++idleTicks > 200) stopHelper();
            return;
        }
        idleTicks = 0;
        if (PLATFORM == Platform.WINDOWS) {
            tickWindows();
            return;
        }
        if (++ticks % 20 != 0 || !querying.compareAndSet(false, true)) return;
        Thread.ofVirtual().name("khanh-spotify-query").start(() -> {
            try {
                state = PLATFORM == Platform.MAC ? queryMac() : PLATFORM == Platform.LINUX ? queryLinux() : SpotifyState.UNAVAILABLE;
            } finally {
                querying.set(false);
            }
        });
    }

    public void playPause() { command("playpause"); }
    public void next() { command("next"); }
    public void previous() { command("previous"); }

    private void command(String command) {
        if (KhanhFeatures.bool("spotify_hud", "demo", false)) return;
        switch (PLATFORM) {
            case WINDOWS -> {
                BufferedWriter input = helperInput;
                if (input == null) return;
                Thread.ofVirtual().name("khanh-spotify-control").start(() -> {
                    try {
                        synchronized (this) {
                            input.write(command);
                            input.newLine();
                            input.flush();
                        }
                    } catch (Exception exception) {
                        KhanhClient.LOGGER.debug("Spotify control skipped: {}", exception.toString());
                    }
                });
            }
            case MAC -> Thread.ofVirtual().name("khanh-spotify-control").start(() -> {
                String verb = switch (command) {
                    case "next" -> "next track";
                    case "previous" -> "previous track";
                    default -> "playpause";
                };
                try {
                    run(Duration.ofSeconds(2), "/usr/bin/osascript", "-e",
                        "if application \"Spotify\" is running then tell application \"Spotify\" to " + verb);
                    state = queryMac();
                } catch (Exception exception) {
                    KhanhClient.LOGGER.debug("Spotify control skipped: {}", exception.toString());
                }
            });
            case LINUX -> Thread.ofVirtual().name("khanh-spotify-control").start(() -> {
                String verb = switch (command) {
                    case "next" -> "next";
                    case "previous" -> "previous";
                    default -> "play-pause";
                };
                try {
                    run(Duration.ofSeconds(2), "playerctl", "-p", "spotify", verb);
                    state = queryLinux();
                } catch (Exception exception) {
                    KhanhClient.LOGGER.debug("Spotify control skipped: {}", exception.toString());
                }
            });
            default -> { }
        }
    }

    // ---------------------------------------------------------------- Windows

    private void tickWindows() {
        Process process = helper;
        if (process != null && process.isAlive() && helperAnyMedia != anyMedia()) stopHelper();   // source changed
        process = helper;
        if (process != null && process.isAlive()) {
            // A helper that stopped talking is restarted.
            if (System.currentTimeMillis() - lastHelperLine > 15_000L) stopHelper();
            return;
        }
        if (System.currentTimeMillis() < helperRetryAt) return;
        helperRetryAt = System.currentTimeMillis() + 10_000L;
        startHelper();
    }

    private synchronized void startHelper() {
        try {
            Path script = Path.of(System.getProperty("java.io.tmpdir"), "khanh-spotify.ps1");
            Files.writeString(script, WINDOWS_HELPER, StandardCharsets.UTF_8);
            ProcessBuilder builder = new ProcessBuilder("powershell.exe", "-NoLogo", "-NoProfile", "-NonInteractive",
                "-ExecutionPolicy", "Bypass", "-WindowStyle", "Hidden", "-File", script.toString())
                .redirectErrorStream(false);
            helperAnyMedia = anyMedia();
            builder.environment().put("KHANH_ANY_MEDIA", helperAnyMedia ? "1" : "0");
            Process process = builder.start();
            helper = process;
            helperInput = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
            lastHelperLine = System.currentTimeMillis();
            if (!shutdownHook) {
                shutdownHook = true;
                Runtime.getRuntime().addShutdownHook(new Thread(this::stopHelper, "khanh-spotify-stop"));
            }
            Thread.ofPlatform().daemon().name("khanh-spotify-read").start(() -> readHelper(process));
            Thread.ofPlatform().daemon().name("khanh-spotify-drain").start(() -> {
                try (var err = process.getErrorStream()) { err.transferTo(java.io.OutputStream.nullOutputStream()); } catch (Exception ignored) { }
            });
        } catch (Exception exception) {
            KhanhClient.LOGGER.debug("Spotify helper could not start: {}", exception.toString());
            state = SpotifyState.UNAVAILABLE;
        }
    }

    private void readHelper(Process process) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lastHelperLine = System.currentTimeMillis();
                state = parse(line.replace("﻿", ""));
            }
        } catch (Exception exception) {
            KhanhClient.LOGGER.debug("Spotify helper stopped: {}", exception.toString());
        }
    }

    private synchronized void stopHelper() {
        Process process = helper;
        helper = null;
        helperInput = null;
        if (process != null) process.destroy();
    }

    // ---------------------------------------------------------------- macOS / Linux

    private static final String MAC_MUSIC_QUERY = """
        if application "Music" is not running then return "INACTIVE"
        tell application "Music"
            if player state is stopped then return "INACTIVE"
            set sep to ASCII character 31
            set stateText to (player state as text)
            set trackName to name of current track
            set artistName to artist of current track
            set positionMillis to (round (player position * 1000))
            set durationMillis to (round ((duration of current track) * 1000))
            return stateText & sep & trackName & sep & artistName & sep & positionMillis & sep & durationMillis & sep & ""
        end tell
        """;

    private static SpotifyState queryMac() {
        try {
            if (anyMedia()) {
                SpotifyState spotify = parse(run(Duration.ofSeconds(2), "/usr/bin/osascript", "-e", MAC_QUERY));
                if (spotify.active()) return spotify;
                return parse(run(Duration.ofSeconds(2), "/usr/bin/osascript", "-e", MAC_MUSIC_QUERY));
            }
            return parse(run(Duration.ofSeconds(2), "/usr/bin/osascript", "-e", MAC_QUERY));
        } catch (Exception exception) {
            KhanhClient.LOGGER.debug("Spotify query skipped: {}", exception.toString());
            return SpotifyState.IDLE;
        }
    }

    private static SpotifyState queryLinux() {
        String format = "{{lc(status)}}" + SEPARATOR + "{{title}}" + SEPARATOR + "{{artist}}" + SEPARATOR
            + "{{position}}" + SEPARATOR + "{{mpris:length}}" + SEPARATOR + "{{mpris:artUrl}}";
        try {
            String output = anyMedia() ? run(Duration.ofSeconds(2), "playerctl", "metadata", "--format", format)
                : run(Duration.ofSeconds(2), "playerctl", "-p", "spotify", "metadata", "--format", format);
            String[] values = output.split(SEPARATOR, -1);
            if (values.length >= 5) {
                // playerctl reports microseconds.
                values[3] = Long.toString(parseLong(values[3]) / 1000L);
                values[4] = Long.toString(parseLong(values[4]) / 1000L);
            }
            return parse(String.join(SEPARATOR, values));
        } catch (java.io.IOException missing) {
            return SpotifyState.NO_PLAYERCTL;
        } catch (Exception exception) {
            return SpotifyState.IDLE;
        }
    }

    // ---------------------------------------------------------------- shared

    private static SpotifyState parse(String output) {
        if (output == null || output.isBlank() || output.equalsIgnoreCase("INACTIVE")) return SpotifyState.IDLE;
        if (output.equalsIgnoreCase("OPEN")) return SpotifyState.OPEN;
        String[] values = output.split(SEPARATOR, -1);
        if (values.length < 5) return SpotifyState.IDLE;
        String playerState = values[0].trim().toLowerCase(Locale.ROOT);
        String title = clean(values[1], "Unknown track");
        String artist = clean(values[2], "Unknown artist");
        long position = Math.max(0L, parseLong(values[3]));
        long duration = Math.max(0L, parseLong(values[4]));
        String art = values.length > 5 ? values[5].trim() : "";
        return new SpotifyState(true, true, playerState.equals("playing"), title, artist,
            Math.min(position, duration > 0 ? duration : position), duration, System.nanoTime(), art);
    }

    private static String run(Duration timeout, String... command) throws Exception {
        Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
        boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Spotify query timed out");
        }
        String output;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            output = reader.lines().reduce("", (left, right) -> left.isEmpty() ? right : left + "\n" + right).trim();
        }
        if (process.exitValue() != 0) throw new IllegalStateException(output.isEmpty() ? "query failed" : output);
        return output;
    }

    private static long parseLong(String value) {
        try {
            return Math.round(Double.parseDouble(value.trim()));
        } catch (RuntimeException exception) {
            return 0L;
        }
    }

    private static Platform detect() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return Platform.WINDOWS;
        if (os.contains("mac")) return Platform.MAC;
        if (os.contains("nux") || os.contains("nix") || os.contains("bsd")) return Platform.LINUX;
        return Platform.OTHER;
    }

    private static String clean(String value, String fallback) {
        String clean = value == null ? "" : value.replace('\n', ' ').replace('\r', ' ').trim();
        return clean.isEmpty() ? fallback : clean;
    }
}
