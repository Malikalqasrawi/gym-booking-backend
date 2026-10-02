package com.mycompany.gymbooking.support;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A small local mail server that speaks just enough SMTP for the app's mail sender: it checks the
 * login like Gmail does and keeps the emails it receives, so email delivery can be tested without
 * sending real email. It can also refuse emails to test retries.
 */
public final class FakeSmtp implements AutoCloseable {

    /** An email as the app sent it. */
    public record Received(String from, List<String> to, MimeMessage message) {
    }

    private final ServerSocket server;
    private final String username;
    private volatile String password;
    private final AtomicInteger refuseNext = new AtomicInteger();
    private final List<Received> received = new CopyOnWriteArrayList<>();

    private FakeSmtp(String username, String password) throws IOException {
        this.username = username;
        this.password = password;
        server = new ServerSocket(0, 50, InetAddress.getLoopbackAddress());
        Thread acceptor = new Thread(this::acceptConnections, "fake-smtp");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    /** A server that accepts only this username and password. */
    public static FakeSmtp start(String username, String password) throws IOException {
        return new FakeSmtp(username, password);
    }

    public int port() {
        return server.getLocalPort();
    }

    /** Like a revoked app password: logins with the old one fail from now on. */
    public void changePassword(String newPassword) {
        password = newPassword;
    }

    /** The next {@code count} emails are refused with "try again later", like a busy server. */
    public void refuseNext(int count) {
        refuseNext.set(count);
    }

    public List<Received> to(String recipient) {
        return received.stream().filter(email -> email.to().contains(recipient)).toList();
    }

    @Override
    public void close() throws IOException {
        server.close();
    }

    private void acceptConnections() {
        while (!server.isClosed()) {
            try (Socket client = server.accept()) {
                talk(client);
            } catch (Exception e) {
                // the server was closed, or the client hung up; wait for the next one
            }
        }
    }

    /** One SMTP conversation: greeting, EHLO, AUTH, then any number of emails until QUIT. */
    private void talk(Socket client) throws Exception {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
        OutputStream out = client.getOutputStream();
        reply(out, "220 fake-smtp ready");
        boolean loggedIn = false;
        String from = null;
        List<String> to = new ArrayList<>();
        String line;
        while ((line = in.readLine()) != null) {
            String command = line.toUpperCase();
            if (command.startsWith("EHLO")) {
                reply(out, "250-fake-smtp\r\n250 AUTH LOGIN PLAIN");
            } else if (command.startsWith("HELO") || command.startsWith("NOOP")) {
                reply(out, "250 OK");
            } else if (command.startsWith("AUTH LOGIN")) {
                reply(out, "334 " + base64("Username:"));
                String user = decode(in.readLine());
                reply(out, "334 " + base64("Password:"));
                loggedIn = checkLogin(out, user, decode(in.readLine()));
            } else if (command.startsWith("AUTH PLAIN")) {
                String[] parts = decode(line.substring("AUTH PLAIN".length()).trim()).split("\0");
                loggedIn = checkLogin(out, parts[1], parts[2]);
            } else if (command.startsWith("MAIL FROM:")) {
                if (!loggedIn) {
                    reply(out, "530 5.7.0 Authentication required");
                } else if (refuseNext.getAndUpdate(n -> Math.max(0, n - 1)) > 0) {
                    reply(out, "451 4.3.0 Mail server busy, try again later");
                } else {
                    from = address(line);
                    to.clear();
                    reply(out, "250 OK");
                }
            } else if (command.startsWith("RCPT TO:")) {
                to.add(address(line));
                reply(out, "250 OK");
            } else if (command.equals("DATA")) {
                reply(out, "354 End data with <CR><LF>.<CR><LF>");
                StringBuilder data = new StringBuilder();
                while (!(line = in.readLine()).equals(".")) {
                    data.append(line.startsWith(".") ? line.substring(1) : line).append("\r\n");
                }
                MimeMessage message = new MimeMessage(Session.getInstance(new Properties()),
                        new ByteArrayInputStream(data.toString().getBytes(StandardCharsets.ISO_8859_1)));
                received.add(new Received(from, List.copyOf(to), message));
                reply(out, "250 OK queued");
            } else if (command.equals("RSET")) {
                from = null;
                to.clear();
                reply(out, "250 OK");
            } else if (command.equals("QUIT")) {
                reply(out, "221 Bye");
                return;
            } else {
                reply(out, "502 Command not implemented");
            }
        }
    }

    private boolean checkLogin(OutputStream out, String user, String pass) throws IOException {
        if (username.equals(user) && password.equals(pass)) {
            reply(out, "235 2.7.0 Accepted");
            return true;
        }
        reply(out, "535 5.7.8 Username and Password not accepted");
        return false;
    }

    private static void reply(OutputStream out, String text) throws IOException {
        out.write((text + "\r\n").getBytes(StandardCharsets.ISO_8859_1));
        out.flush();
    }

    /** "MAIL FROM:<gym@test.com> SIZE=123" -> "gym@test.com" */
    private static String address(String line) {
        return line.substring(line.indexOf('<') + 1, line.indexOf('>'));
    }

    private static String base64(String text) {
        return Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8));
    }

    private static String decode(String base64) {
        return new String(Base64.getDecoder().decode(base64.trim()), StandardCharsets.UTF_8);
    }
}
