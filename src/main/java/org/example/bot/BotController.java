package org.example.bot;

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.request.InlineKeyboardButton;
import com.pengrad.telegrambot.model.request.InlineKeyboardMarkup;
import com.pengrad.telegrambot.model.request.Keyboard;
import com.pengrad.telegrambot.model.request.ReplyKeyboardMarkup;
import com.pengrad.telegrambot.request.EditMessageText;
import com.pengrad.telegrambot.request.SendMessage;
import com.pengrad.telegrambot.request.SendVideo;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.pengrad.telegrambot.model.request.ParseMode.HTML;
import static org.example.bot.DateUtil.isWithinTradingHours;
import static org.example.bot.JedisActions.*;


public class BotController {

    public static JedisPool jedisPool;
    public static final String USER_DB_MAP_KEY = "userDBMap";

    public static void main(String[] args) throws URISyntaxException {
        String TOKEN = "";
        String AdminID = "6473681788";

        try {
            String configFilePath = "src/config.properties";
            FileInputStream propsInput = new FileInputStream(configFilePath);
            Properties prop = new Properties();
            prop.load(propsInput);
            TOKEN = prop.getProperty("TOKEN");

        } catch (IOException e) {
            e.printStackTrace();
        }

        String redisUriString = System.getenv("REDIS_URL");
        jedisPool = new JedisPool(new URI(redisUriString));

        TelegramBot bot = new TelegramBot(TOKEN);

        bot.setUpdatesListener(updates -> {
            try (Jedis jedis = jedisPool.getResource()) {
                updates.forEach(update -> {
                    String playerName = "Trader";
                    long playerId;
                    String messageText = "";
                    String messageCallbackText = "";
                    String uid;
                    int messageId;
                    Path resourcePath = Paths.get("src/main/resources");
                    File videoDepositFile = resourcePath.resolve("deposit.MP4").toFile();
                    File videoRegistrationFile = resourcePath.resolve("registartion.MP4").toFile();
                    File videoExampleFile = resourcePath.resolve("example.mp4").toFile();

                    if (update.callbackQuery() == null && (update.message() == null || update.message().text() == null)) {
                        return;
                    }

                    if (update.callbackQuery() == null) {
                        playerName = update.message().from().firstName();
                        playerId = update.message().from().id();
                        messageText = update.message().text();
                        messageId = update.message().messageId();
                    } else if (update.message() == null) {
                        playerName = update.callbackQuery().from().firstName();
                        playerId = update.callbackQuery().from().id();
                        messageCallbackText = update.callbackQuery().data();
                        messageId = update.callbackQuery().message().messageId();
                    } else {
                        messageId = 0;
                        playerId = 0L;
                    }

                    if (playerId != Long.parseLong(AdminID)) {
                        try {
                            String userKey = USER_DB_MAP_KEY + ":" + playerId;
                            User checkedUser = convertJsonToUser(jedis.get(userKey));
                            Date date = new Date();
                            checkedUser.setLastTimeTexted(date);
                            String updatedUser = convertUserToJson(checkedUser);
                            jedis.set(userKey, updatedUser);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    } else {
                        System.out.println("Admin there");
                    }


                    try {
                        User checkedAdmin = convertJsonToUser(jedis.get(AdminID));
                        Date currentDate = new Date();
                        Date checkAdminDate = DateUtil.addDays(checkedAdmin.getLastTimeTexted(), 2);
                        System.out.println("Im not there");
                        if (checkAdminDate.getTime() < currentDate.getTime()) {
                            System.out.println("Im there");
                            checkedAdmin.setLastTimeTexted(currentDate);
                            jedis.set(AdminID, convertUserToJson(checkedAdmin));
                            System.out.println("Admin done");
                            Set<String> userKeys = jedis.keys("userDBMap:*");
                            System.out.println("Keys done");
                            System.out.println(userKeys.size());
                            for (String keyForUser : userKeys) {
                                User currentUser = convertJsonToUser(jedis.get(keyForUser));
                                if (currentUser.getLastTimeTexted() != null && currentUser.getTimesTextWasSent() != 0) {
                                    Date checkUserDate = DateUtil.addDays(currentUser.getLastTimeTexted(), 1);
                                    if (checkUserDate.getTime() < currentDate.getTime()) {
                                        String userTgID = keyForUser.substring(10);
                                        if (currentUser.isDeposited() && currentUser.getTimesTextWasSent() == 1) {
                                            bot.execute(new SendMessage(userTgID, "\uD83D\uDCC8\uD83D\uDCB8\uD83D\uDD52 I've received an update, and now my signals are even more accurate! " +
                                                    "This presents a fantastic opportunity to earn money. Let's give trading a shot for the next 8 hours.").parseMode(HTML));
                                            increaseTimesWasSent(keyForUser);
                                        } else if (currentUser.isDeposited() && currentUser.getTimesTextWasSent() == 2) {
                                            bot.execute(new SendMessage(userTgID, "\uD83D\uDCC8 The market is currently in fantastic shape! It's the perfect time " +
                                                    "to trade and potentially make some easy money! " +
                                                    " There are only 4 hours left until the market is expected to be awesome. Don't miss out! \uD83D\uDCB0\uD83D\uDD52").parseMode(HTML));
                                            increaseTimesWasSent(keyForUser);
                                        } else if (currentUser.isRegistered() && currentUser.getTimesTextWasSent() == 1) {
                                            bot.execute(new SendMessage(userTgID, "\uD83D\uDE80\uD83E\uDD11 You're almost there, just one more step to start receiving signals! " +
                                                    "It's quick and convenient for you.").parseMode(HTML));
                                            increaseTimesWasSent(keyForUser);
                                        } else if (currentUser.isRegistered() && currentUser.getTimesTextWasSent() == 2) {
                                            bot.execute(new SendMessage(userTgID, "\uD83D\uDCB0\uD83D\uDCC8 It appears you're eager to start earning. Once you've made your deposit, you'll gain " +
                                                    "access to my accurate signals, and we can begin trading. Let's get started! ").parseMode(HTML));
                                            increaseTimesWasSent(keyForUser);
                                        } else if (!currentUser.isRegistered() && currentUser.getTimesTextWasSent() == 1) {
                                            bot.execute(new SendMessage(userTgID, "\uD83D\uDCCA\uD83D\uDD17 I'd like to remind you that for registration, you should create a new account " +
                                                    "using this link: [https://bit.ly/SiriTradeBot](https://bit.ly/SiriTradeBot). It'll only take a couple of minutes, " +
                                                    "and I'm ready to receive your signals once it's done. Let's proceed! ").parseMode(HTML));
                                            increaseTimesWasSent(keyForUser);
                                        } else if (!currentUser.isRegistered() && currentUser.getTimesTextWasSent() == 2) {
                                            bot.execute(new SendMessage(userTgID, "⏳\uD83D\uDCCA\uD83D\uDD17 I want to emphasize that signing up is a quick process! Simply create a new " +
                                                    "account using this link: [https://bit.ly/SiriTradeBot](https://bit.ly/SiriTradeBot). (This is the final reminder, " +
                                                    "if you don't manage to create an account within the next 3 days, you won't get access to my signals) ").parseMode(HTML));
                                            increaseTimesWasSent(keyForUser);
                                        }
                                    }
                                }
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    if (String.valueOf(playerId).equals(AdminID)) {
                        if (messageText.startsWith("A") || messageText.startsWith("a") || messageText.startsWith("Ф") || messageText.startsWith("ф")) {
                            try {
                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                InlineKeyboardButton button7 = new InlineKeyboardButton("Deposit done!");
                                button7.callbackData("IDeposit");
                                inlineKeyboardMarkup.addRow(button7);
                                System.out.println(messageText.length());
                                String tgID = messageText.substring(1);
                                System.out.println(tgID);
                                registrationApprove(Long.parseLong(tgID));
                                registrationApprove(Long.parseLong(tgID));
                                bot.execute(new SendMessage(tgID, "✅ Fantastic, your account is confirmed! T" +
                                        "he final step is to make a deposit of at least $50 using any convenient method. " +
                                        "After that, click the 'Deposit done' button. \uD83D\uDCB0\uD83D\uDC4D\n" + "\n" +
                                        "\uD83C\uDF1F\uD83D\uDCB8 I'd like to mention that it's recommended to start with a deposit of $50 - $350." +
                                        "\n"
                                ).replyMarkup(inlineKeyboardMarkup));
                                bot.execute(new SendVideo(tgID, videoDepositFile));
                                bot.execute(new SendMessage(tgID, "☝️ Here is a video guide on how to make a deposit.").parseMode(HTML));
                                bot.execute(new SendMessage(AdminID, "Registration for " + tgID + " was approved"));
                                setTo1TimesWasSent(tgID);
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("reply:")) {
                            int indexOfAnd = messageText.indexOf("&");
                            String tgID = messageText.substring(6, indexOfAnd);
                            String reply = messageText.substring(indexOfAnd + 1);
                            System.out.println(indexOfAnd + "\n" + tgID + "\n" + reply);
                            bot.execute(new SendMessage(tgID, reply));
                            bot.execute(new SendMessage(AdminID, "Reply was sent"));
                        } else if (messageText.startsWith("deleteUser:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(11));
                                jedis.del(TGId);
                                bot.execute(new SendMessage(AdminID, "User with ID " + TGId + " was fully deleted"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("banSupport:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(11));
                                User userBanned = convertJsonToUser(jedis.get(TGId));
                                userBanned.setCanWriteToSupport(false);
                                String updatedBannedUser = convertUserToJson(userBanned);
                                jedis.set(TGId, updatedBannedUser);
                                bot.execute(new SendMessage(AdminID, "User with ID " + TGId + " was banned to write to support"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("giveAdvanced:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(13));
                                User userUpdated = convertJsonToUser(jedis.get(TGId));
                                userUpdated.setTariffUsed(1);
                                userUpdated.setMessagesAfterDeposit(10);
                                String updatedUser = convertUserToJson(userUpdated);
                                jedis.set(TGId, updatedUser);
                                bot.execute(new SendMessage(AdminID, "User with ID " + messageText.substring(13) + " now has advanced plan!"));
                                bot.execute(new SendMessage(messageText.substring(13), "✅ Plan 'Advanced' has been activated!"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("givePro:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(8));
                                User userUpdated = convertJsonToUser(jedis.get(TGId));
                                userUpdated.setTariffUsed(2);
                                userUpdated.setMessagesAfterDeposit(10);
                                String updatedUser = convertUserToJson(userUpdated);
                                jedis.set(TGId, updatedUser);
                                bot.execute(new SendMessage(AdminID, "User with ID " + messageText.substring(8) + " now has pro plan!"));
                                bot.execute(new SendMessage(messageText.substring(8), "✅ Plan 'Pro' has been activated!"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("giveBasic:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(10));
                                User userUpdated = convertJsonToUser(jedis.get(TGId));
                                userUpdated.setTariffUsed(0);
                                String updatedUser = convertUserToJson(userUpdated);
                                jedis.set(TGId, updatedUser);
                                bot.execute(new SendMessage(AdminID, "User with ID " + TGId + " now has basic plan!"));
                                bot.execute(new SendMessage(messageText.substring(10), "✅ Plan 'Basic' has been activated!"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("banDeposit30:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(13));
                                User userBanned = convertJsonToUser(jedis.get(TGId));
                                Date currentDate = new Date();
                                userBanned.setLastTimePressedDeposit(DateUtil.addMinutes(currentDate, 30));
                                String updatedBannedUser = convertUserToJson(userBanned);
                                jedis.set(TGId, updatedBannedUser);
                                bot.execute(new SendMessage(AdminID, "User with ID " + TGId + " was banned to press button 'Deposit done' for 30 minutes. "));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("deleteDeposit:")) {
                            try {
                                String TGId = (messageText.substring(14));
                                depositDisapprove(Long.parseLong(TGId));
                                System.out.println(TGId);
                                bot.execute(new SendMessage(AdminID, "User with ID " + TGId + " got deleted"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again.  "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("deleteRegistration:")) {
                            try {
                                String TGId = (messageText.substring(19));
                                registrationDisapprove(Long.parseLong(TGId));
                                System.out.println(TGId);
                                bot.execute(new SendMessage(AdminID, "User with ID " + TGId + " got register disapprove"));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("getUserName:")) {
                            try {
                                String TGId = USER_DB_MAP_KEY + ":" + (messageText.substring(12));
                                User newUser = convertJsonToUser(jedis.get(TGId));
                                bot.execute(new SendMessage(AdminID, "Name of user is: " + newUser.getName() + " his TG id: " + TGId));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("setCheckForUID:")) {
                            try {
                                long newCheck = Integer.parseInt(messageText.substring(15));
                                User adminUser = convertJsonToUser(jedis.get(AdminID));
                                adminUser.setUID(String.valueOf(newCheck));
                                String updatedAdminUser = convertUserToJson(adminUser);
                                jedis.set(AdminID, updatedAdminUser);
                                bot.execute(new SendMessage(AdminID, "First numbers is: " + newCheck + "."));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("createNewPost:")) {
                            try {
                                String postText = messageText.substring(14);
                                Set<String> userKeys = jedis.keys("userDBMap:*");
                                System.out.println("Amount of users: " + userKeys.size());
                                for (String keyForUser : userKeys) {
                                    String userTgID = keyForUser.substring(10);
                                    bot.execute(new SendMessage(userTgID, postText));
                                }
                                bot.execute(new SendMessage(AdminID, "The message " + postText + " has been sent."));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("D") || messageText.startsWith("d") || messageText.startsWith("В") || messageText.startsWith("в")) {
                            String tgID = messageText.substring(1);
                            InlineKeyboardButton button12 = new InlineKeyboardButton("Register here");
                            InlineKeyboardButton button13 = new InlineKeyboardButton("Registered");
                            button12.url("https://bit.ly/SiriTradeBot");
                            button13.callbackData("ImRegistered");
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            inlineKeyboardMarkup.addRow(button12, button13);
                            bot.execute(new SendMessage(tgID, "❌ Your ID is invalid. Please make sure you registered using the 'Register here' " +
                                    "button and sent a new UID. After registering, press 'Registered' again. \uD83D\uDE4F\n" + "\n" +
                                    "If you're still facing issues, please contact support by using the command /help. They'll be able to assist you further. ").replyMarkup(inlineKeyboardMarkup));
                            bot.execute(new SendMessage(AdminID, "Registration for " + tgID + " was disapproved"));
                            bot.execute(new SendVideo(tgID, videoRegistrationFile));
                            bot.execute(new SendMessage(tgID, "☝️ Here is a video guide on how to register.").parseMode(HTML));
                        } else if (messageText.startsWith("Y") || messageText.startsWith("y") || messageText.startsWith("Н") || messageText.startsWith("н")) {
                            try {
                                String tgID = messageText.substring(1);
                                depositApprove(Long.parseLong(tgID));
                                depositApprove(Long.parseLong(tgID));
                                Keyboard replyKeyboardMarkup = (Keyboard) new ReplyKeyboardMarkup(
                                        new String[]{"/newsignal"});
                                bot.execute(new SendMessage(AdminID, "Deposit for " + tgID + " was approved"));
                                bot.execute(new SendMessage(tgID, "\uD83D\uDE80\uD83D\uDCCA Awesome! Everything is set up and ready to go! You can start receiving signals now. Just click on '/newsignal' or type it manually.  \n" +
                                        "\n" +
                                        "<b>❗️IMPORTANT ❗️</b> \n\n" +
                                        "<i>1⃣ I am analyzing only the real market, so I won't work on a demo properly. To achieve better accuracy, trade on a real account.\n\n" +
                                        "2⃣ I analyze all successful and failed signals. The more signals you get, the better they become.\n\n" +
                                        "3⃣ The recommended amount to use for trading is 15-20% per trade.</i>\n\n" +
                                        "Below is a video guide on how to use signals from me. \n" + "\n" +
                                        "If you're still facing issues, please contact support by using the command /help. They'll be able to assist you further.").parseMode(HTML).replyMarkup(replyKeyboardMarkup));
                                setTo1TimesWasSent(tgID);
                                depositApprove(Long.parseLong(tgID));
                                try {
                                    Thread.sleep(1000);
                                } catch (InterruptedException e) {
                                    bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                    e.printStackTrace();
                                }
                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                InlineKeyboardButton button22 = new InlineKeyboardButton("Basic - 0$");
                                button22.callbackData("Basic");
                                InlineKeyboardButton button23 = new InlineKeyboardButton("Advanced - 35$");
                                button23.callbackData("Advanced");
                                InlineKeyboardButton button24 = new InlineKeyboardButton("Pro - 60$");
                                button24.callbackData("Pro");
                                inlineKeyboardMarkup.addRow(button22);
                                inlineKeyboardMarkup.addRow(button23);
                                inlineKeyboardMarkup.addRow(button24);
                                bot.execute(new SendMessage(tgID, "\uD83D\uDE80 Please choose the plan you'd like to work with!\uD83D\uDCCB\n" +
                                        "<b>Basic - Signals with accuracy from 50% to 94% - Price: $0 \uD83C\uDD93\n" +
                                        "Advanced - Signals with accuracy over 80% - Price: $35 \uD83D\uDE80\n" +
                                        "Pro - Signals with accuracy over 95% - Price: $60</b> \uD83D\uDCAF\n\n" +
                                        "<i>Please choose the plan that suits you best! </i> \n\n You can always change the plan via /upgrade command. \uD83D\uDE04\uD83D\uDC4D").parseMode(HTML).replyMarkup(inlineKeyboardMarkup));

                            } catch (Exception e) {
                                bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageText.startsWith("N") || messageText.startsWith("n") || messageText.startsWith("Т") || messageText.startsWith("т")) {
                            String tgID = messageText.substring(1);
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            InlineKeyboardButton button7 = new InlineKeyboardButton("Deposit done");
                            button7.callbackData("IDeposit");
                            inlineKeyboardMarkup.addRow(button7);
                            bot.execute(new SendMessage(tgID, "❌ Something went wrong. Please ensure that you have deposited at least $50 into the new account " +
                                    "you created through the link, and then click on 'Deposit done'. \uD83D\uDCE5\uD83D\uDCB0✔\uFE0F").replyMarkup(inlineKeyboardMarkup));
                            bot.execute(new SendMessage(AdminID, "Deposit for " + tgID + " was disapproved"));
                            bot.execute(new SendVideo(tgID, videoDepositFile));
                            bot.execute(new SendMessage(tgID, "☝️ Here is a video guide on how to make a deposit.").parseMode(HTML));
                        }
                    } else if (messageText.startsWith("#question:")) {
                        String userQuestion = messageText.substring(10);
                        if (userCanWriteToSupport(playerId)) {
                            bot.execute(new SendMessage(playerId, "✅ Our admin will reply you shortly!" + userQuestion).parseMode(HTML));
                            bot.execute(new SendMessage(AdminID, "✅ ID:<code>" + playerId + "</code> has a question" + userQuestion + " To answer it write a message: <code>reply:111111111&</code> *your text*").parseMode(HTML));
                        } else {
                            bot.execute(new SendMessage(playerId, "❌ There was an issue. The support is currently unavailable. Please try again later. ").parseMode(HTML));
                        }
                    } else if (messageText.equals("/help") || messageCallbackText.equals("Help")) {
                        bot.execute(new SendMessage(playerId, "\uD83D\uDC4B Welcome to Customer Support! If you have a question, please use the command #question: Copy the command <code>#question:</code> , " +
                                "paste <code>#question:</code> in the chat, write your question, send the message, and wait for our admin to respond shortly. \uD83D\uDCE9").parseMode(HTML));
                    } else if (messageText.equals("/start")) {
                        InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                        InlineKeyboardButton button32 = new InlineKeyboardButton("Next Step!");
                        button32.callbackData("RegisterMe");
                        inlineKeyboardMarkup.addRow(button32);
                        bot.execute(new SendMessage(playerId, "\uD83D\uDC4B Hey, " + playerName + "\n" +
                                "\n" +
                                "\uD83D\uDCC8 I am the Siri Trading Bot, and I am created to provide highly accurate trading signals. " +
                                "I use market analysis to calculate the probabilities of where currency pairs might go. " +
                                "All you need to do is simply copy my signals and start making money!\uD83D\uDCC8 \n" +
                                "\n" +
                                "To begin receiving signals, just start by clicking on 'Next Step!'. \uD83D\uDCCA\n").replyMarkup(inlineKeyboardMarkup).parseMode(HTML));
                        bot.execute(new SendMessage(playerId, "If you ever run into any issues or have suggestions, " +
                                "you can reach out to our bot support using the /help command. ❗\uFE0F").parseMode(HTML));
                        bot.execute(new SendVideo(playerId, videoExampleFile));
                        bot.execute(new SendMessage(playerId, "☝️ Here is a video example of how I work.").parseMode(HTML));
                    } else if (userDeposited(playerId) || userDeposited(playerId)) {
                        if (messageText.equals("/newSignal") || messageCallbackText.equals("getSignal") || messageText.equals("/newsignal")) {
                            String userKey = USER_DB_MAP_KEY + ":" + playerId;
                            User currentUser = convertJsonToUser(jedis.get(userKey));
                            //   LocalTime currentTime = LocalTime.now().withNano(0).withSecond(0);
                            TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
                            Instant currentInstant = Instant.now();
                            LocalTime currentTime = currentInstant.atZone(ZoneId.of("UTC")).toLocalTime();
                            List<String> listOfPairs = new ArrayList<>();
                            int modeChoose = currentUser.getModeChoose();

                            if (modeChoose == 1) {
                                listOfPairs.addAll(Arrays.asList(
                                        "AUD/CAD OTC", "AUD/CHF OTC", "AUD/JPY OTC", "CAD/CHF OTC",
                                        "CAD/JPY OTC", "CHF/JPY OTC", "EUR/CAD OTC", "EUR/CHF OTC",
                                        "GBP/AUD OTC", "NZD/CAD OTC", "NZD/CHF OTC", "NZD/JPY OTC",
                                        "USD/BRL OTC", "USD/CAD OTC", "AUD/USD OTC", "USD/INR OTC",
                                        "EUR/AUD OTC", "GBP/JPY OTC"
                                ));
                            } else if (modeChoose == 2 && isWithinTradingHours(currentTime)) {
                                listOfPairs.addAll(Arrays.asList(
                                        "EUR/AUD", "EUR/GBP", "EUR/JPY", "EUR/USD", "GBP/CAD",
                                        "GBP/JPY", "GBP/USD", "USD/JPY", "CAD/JPY"
                                ));
                            } else if (modeChoose == 3 && isWithinTradingHours(currentTime)) {
                                listOfPairs.addAll(Arrays.asList(
                                        "AUD/CAD OTC", "AUD/CHF OTC", "AUD/JPY OTC", "CAD/CHF OTC",
                                        "CAD/JPY OTC", "CHF/JPY OTC", "EUR/CAD OTC", "EUR/CHF OTC",
                                        "GBP/AUD OTC", "NZD/CAD OTC", "NZD/CHF OTC", "NZD/JPY OTC",
                                        "USD/BRL OTC", "USD/CAD OTC", "AUD/USD OTC", "USD/INR OTC",
                                        "EUR/AUD OTC", "GBP/JPY OTC", "EUR/AUD", "EUR/GBP", "EUR/JPY",
                                        "EUR/USD", "GBP/CAD", "GBP/JPY", "GBP/USD", "USD/JPY"
                                ));
                            } else {
                                bot.execute(new SendMessage(playerId, "❌ You can't currently trade with your mode. Use /changemode command to change it."));
                                return;
                            }

                            Runnable signalGeneratorTask = () -> {
                                int planChoose = 0;
                                int messagesAfterDeposit = 0;
                                try {
                                    planChoose = currentUser.getTariffUsed();
                                    messagesAfterDeposit = currentUser.getMessagesAfterDeposit();
                                } catch (Exception e) {
                                    currentUser.setTariffUsed(0);
                                    currentUser.setMessagesAfterDeposit(0);
                                    jedis.set(userKey, convertUserToJson(currentUser));
                                    bot.execute(new SendMessage(AdminID, "❌ There was an issue. Please try again. "));
                                    e.printStackTrace();
                                }
                                bot.execute(new SendMessage(playerId, "\uD83D\uDFE2").parseMode(HTML));
                                try {
                                    Thread.sleep(1500);
                                } catch (InterruptedException e) {
                                    bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                    e.printStackTrace();
                                }
                                Random random = new Random();
                                int randomNumber = random.nextInt(listOfPairs.size());
                                int randomUp = random.nextInt(2);
                                String direction;
                                if (randomUp == 0) {
                                    direction = "\uD83D\uDFE2⬆\uFE0F Signal: <b>UP</b> ";
                                } else {
                                    direction = "\uD83D\uDD34⬇\uFE0F Signal: <b>DOWN</b> ";
                                }
                                int randomAddTime = random.nextInt(10000) + 8000;
                                int randomTime = random.nextInt(3) + 1;
                                int randomAccuracy = 50;
                                if (planChoose == 0) {
                                    randomAccuracy = random.nextInt(44) + 50;
                                    if (randomAccuracy >= 80) {
                                        randomAccuracy = random.nextInt(44) + 50;
                                    }
                                    if (randomAccuracy >= 80) {
                                        randomAccuracy = random.nextInt(44) + 50;
                                    }
                                    if (randomAccuracy >= 80) {
                                        randomAccuracy = random.nextInt(44) + 50;
                                    }
                                } else if (planChoose == 1) {
                                    randomAccuracy = random.nextInt(19) + 80;
                                    if (randomAccuracy >= 90) {
                                        randomAccuracy = random.nextInt(19) + 80;
                                    }
                                } else if (planChoose == 2) {
                                    randomAccuracy = random.nextInt(5) + 95;
                                }


                                String pickedPair = listOfPairs.get(randomNumber);
                                EditMessageText editMessageText = new EditMessageText(playerId, messageId + 1, "\uD83D\uDFE2\uD83D\uDFE2").parseMode(HTML);
                                bot.execute(editMessageText);
                                try {
                                    Thread.sleep(1500);
                                } catch (InterruptedException e) {
                                    bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again."));
                                    e.printStackTrace();
                                }
                                EditMessageText editMessageTex = new EditMessageText(playerId, messageId + 1, "\uD83D\uDFE2\uD83D\uDFE2\uD83D\uDFE2").parseMode(HTML);
                                bot.execute(editMessageTex);
                                try {
                                    Thread.sleep(1500);
                                } catch (InterruptedException e) {
                                    bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again."));
                                    e.printStackTrace();
                                }
                                EditMessageText editMessageText4 = new EditMessageText(playerId, messageId + 1, "\uD83D\uDCC8The <b>" + pickedPair + "</b> asset is currently being analyzed.").parseMode(HTML);
                                bot.execute(editMessageText4);
                                try {
                                    Thread.sleep(3000);
                                } catch (InterruptedException e) {
                                    bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again."));
                                    e.printStackTrace();
                                }
                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                InlineKeyboardButton button22 = new InlineKeyboardButton("Get new signal");
                                button22.callbackData("getSignal");
                                inlineKeyboardMarkup.addRow(button22);
                                EditMessageText editMessage = new EditMessageText(playerId, messageId + 1, "\uD83D\uDE80 Trading Alert: \n\uD83D\uDCC8 Asset: <b>" + pickedPair + "</b>\n" + direction + "\n⏳ Duration: <b> " + randomTime + " Minutes </b>\n\uD83C\uDFAF Accuracy: <b> " + randomAccuracy + "%</b>\n\n\uD83D\uDEA6 Trading Tips:\n" +
                                        "\uD83D\uDC41\u200D\uD83D\uDDE8 Await the 'Start!' signal,\n" +
                                        "\uD83D\uDCAC Make your prediction,\n" +
                                        "\uD83D\uDCBC Secure your position, and\n" +
                                        "\uD83D\uDCB0 Trade smartly!").parseMode(HTML);
                                bot.execute(editMessage);
                                try {
                                    Thread.sleep(randomAddTime);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                                Keyboard replyKeyboardMarkup = (Keyboard) new ReplyKeyboardMarkup(
                                        new String[]{"/newsignal"});
                                bot.execute(new SendMessage(playerId, "<b>Start!</b>").replyMarkup(replyKeyboardMarkup).parseMode(HTML));
                                if (planChoose == 0) {
                                    System.out.println("Im planChoose == 0");
                                    System.out.println(messagesAfterDeposit);
                                    if (messagesAfterDeposit < 5) {
                                        currentUser.setMessagesAfterDeposit(messagesAfterDeposit + 1);
                                        jedis.set(userKey, convertUserToJson(currentUser));
                                        System.out.println("Deposit +1");
                                    } else if (messagesAfterDeposit == 5) {
                                        System.out.println("Done!");
                                        InlineKeyboardMarkup inlineKeyboardMark = new InlineKeyboardMarkup();
                                        InlineKeyboardButton button2 = new InlineKeyboardButton("Basic - 0$");
                                        button2.callbackData("Basic");
                                        InlineKeyboardButton button3 = new InlineKeyboardButton("Advanced - 35$");
                                        button3.callbackData("Advanced");
                                        InlineKeyboardButton button4 = new InlineKeyboardButton("Pro - 60$");
                                        button4.callbackData("Pro");
                                        inlineKeyboardMark.addRow(button2);
                                        inlineKeyboardMark.addRow(button3);
                                        inlineKeyboardMark.addRow(button4);
                                        bot.execute(new SendMessage(playerId, "<b>\uD83D\uDE0A I want to remind you that you " +
                                                "can improve the accuracy of signals!" +
                                                " To do that, you just need to change your plan. Simply click on the plan that " +
                                                "suits you best from the options below.\uD83D\uDCC8. You also can do it via /upgrade command.\n\n</b>" +
                                                "<b>Basic - Signals with accuracy from 50% to 94% - Price: $0 \uD83C\uDD93\n" +
                                                "Advanced - Signals with accuracy over 80% - Price: $35 \uD83D\uDE80\n" +
                                                "Pro - Signals with accuracy over 95% - Price: $60</b> \uD83D\uDCAF\n\n")
                                                .replyMarkup(replyKeyboardMarkup).parseMode(HTML).replyMarkup(inlineKeyboardMark));
                                        System.out.println("Done 2");
                                    }
                                }
                            };
                            new Thread(signalGeneratorTask).start();
                        } else if (messageText.equals("/changeMode") || messageText.equals("/changemode")) {
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            InlineKeyboardButton button22 = new InlineKeyboardButton("OTC");
                            button22.callbackData("OTC");
                            InlineKeyboardButton button23 = new InlineKeyboardButton("None OTC");
                            button23.callbackData("noneOTC");
                            InlineKeyboardButton button24 = new InlineKeyboardButton("Both");
                            button24.callbackData("both");
                            inlineKeyboardMarkup.addRow(button22, button23, button24);
                            bot.execute(new SendMessage(playerId, "<b>Please choose your mode!</b>").parseMode(HTML).replyMarkup(inlineKeyboardMarkup));
                        } else if (messageText.equals("/upgrade")) {
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            InlineKeyboardButton button22 = new InlineKeyboardButton("Basic - 0$");
                            button22.callbackData("Basic");
                            InlineKeyboardButton button23 = new InlineKeyboardButton("Advanced - 35$");
                            button23.callbackData("Advanced");
                            InlineKeyboardButton button24 = new InlineKeyboardButton("Pro - 60$");
                            button24.callbackData("Pro");
                            inlineKeyboardMarkup.addRow(button22);
                            inlineKeyboardMarkup.addRow(button23);
                            inlineKeyboardMarkup.addRow(button24);
                            bot.execute(new SendMessage(playerId, "\uD83D\uDE80 Please choose the plan you'd like to work with!\uD83D\uDCCB\n" +
                                    "<b>Basic - Signals with accuracy from 50% to 94% - Price: $0 \uD83C\uDD93\n" +
                                    "Advanced - Signals with accuracy over 80% - Price: $35 \uD83D\uDE80\n" +
                                    "Pro - Signals with accuracy over 95% - Price: $60</b> \uD83D\uDCAF\n\n" +
                                    "<i>Please choose the plan that suits you best! </i> \uD83D\uDE04\uD83D\uDC4D").parseMode(HTML).replyMarkup(inlineKeyboardMarkup));
                        } else if (messageCallbackText.equals("OTC")) {
                            try {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                currentUser.setModeChoose(1);
                                jedis.set(userKey, convertUserToJson(currentUser));
                                bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 You successfully picked 'OTC' mode! Now you will get only OTC signals. " +
                                        "To change it use /changemode command.</b>").parseMode(HTML));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }

                        } else if (messageCallbackText.equals("noneOTC")) {
                            try {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                currentUser.setModeChoose(2);
                                jedis.set(userKey, convertUserToJson(currentUser));
                                bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 You successfully picked 'None OTC' mode! Now you will get " +
                                        "only none OTC signals. To change it use /changemode command.</b>").parseMode(HTML));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageCallbackText.equals("both")) {
                            try {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                currentUser.setModeChoose(3);
                                jedis.set(userKey, convertUserToJson(currentUser));
                                bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 You successfully picked 'Both' mode! Now you will get all available signals. " +
                                        "To change it use /changemode command.</b>").parseMode(HTML));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }

                        } else if (messageCallbackText.equals("Basic")) {
                            try {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                if (currentUser.getTariffUsed() != 0) {
                                    bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 You shouldn't pick lower plan.</b>").parseMode(HTML));
                                } else {
                                    bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 Great choice! Your \"Basic\" plan is now activated. Remember, you can always upgrade it using the /upgrade command.</b>").parseMode(HTML));
                                }
                            } catch (Exception e) {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                currentUser.setTariffUsed(0);
                                currentUser.setMessagesAfterDeposit(0);
                                jedis.set(userKey, convertUserToJson(currentUser));
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageCallbackText.equals("Advanced")) {
                            try {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                if (currentUser.getTariffUsed() == 2) {
                                    bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 You shouldn't pick lower plan.</b>").parseMode(HTML));
                                } else {
                                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                    InlineKeyboardButton button22 = new InlineKeyboardButton("Next!");
                                    button22.callbackData("Next");
                                    inlineKeyboardMarkup.addRow(button22);
                                    bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 Awesome! You have chosen the \"Advanced\" plan. " +
                                            "Now, to obtain it, please pay $35 using your preferred payment method below.\n\n" +
                                            "<b>BTC</b>\n<code>1Kws5g3yHN5G2Q8Tu4dShv2VSWxc87k8Cj</code>\n\n" +
                                            "<b>USDT TRC20</b>\n<code>TE67mnapG82EscJwpZfByuvKh6UmXkPy7q</code>\n\n " +
                                            "<i>Important! Please consider any transaction fees," +
                                            " if the amount received is less than the required sum, the plan won't be activated! </i>\n\n \uD83D\uDE0A\uD83D\uDCB3\uD83D\uDE80 " +
                                            "After making the payment, click the \"Next!\" button.</b>").parseMode(HTML).replyMarkup(inlineKeyboardMarkup));
                                }
                            } catch (Exception e) {
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User currentUser = convertJsonToUser(jedis.get(userKey));
                                currentUser.setTariffUsed(0);
                                currentUser.setMessagesAfterDeposit(0);
                                jedis.set(userKey, convertUserToJson(currentUser));
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageCallbackText.equals("Pro")) {
                            try {

                                InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                InlineKeyboardButton button22 = new InlineKeyboardButton("Next!");
                                button22.callbackData("Next");
                                inlineKeyboardMarkup.addRow(button22);
                                bot.execute(new SendMessage(playerId, "<b>\uD83D\uDFE2 Awesome! You have chosen the \"Pro\" plan. " +
                                        "Now, to obtain it, please pay $60 using your preferred payment method below. " +
                                        "\n\n<b>BTC</b>\n<code>1Kws5g3yHN5G2Q8Tu4dShv2VSWxc87k8Cj</code>\n\n<b>USDT TRC20</b>" +
                                        "\n<code>TE67mnapG82EscJwpZfByuvKh6UmXkPy7q</code>\n\n  " +
                                        "<i>Important! Please consider any transaction fees," +
                                        " if the amount received is less than the required sum, the plan won't be activated! </i>\n\n \uD83D\uDE0A\uD83D\uDCB3\uD83D\uDE80 " +
                                        "After making the payment, click the \"Next!\" button.</b>").parseMode(HTML).replyMarkup(inlineKeyboardMarkup));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (messageCallbackText.equals("Next")) {
                            try {
                                bot.execute(new SendMessage(playerId, "\uD83D\uDE0A Now, please follow the instructions carefully!\n" +
                                        "\n" +
                                        "1. Take a screenshot confirming your payment. It should show the amount and transaction number.\n" +
                                        "\n" +
                                        "2. Send this screenshot along with your ID <code>" + playerId + "</code> for verification to our admins @TeamLeadCEO. " +
                                        "<i>It's important to send ONLY the screenshot and ID. They won't respond to other questions.</i>\uD83D\uDCBC\n" +
                                        "\n" +
                                        "Wait for confirmation that the plan is activated, and start earning! \uD83D\uDC4D").parseMode(HTML));
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        }

                    } else if (userRegistered(playerId)) {
                        if (messageCallbackText.equals("IDeposit")) {
                            try {
                                Date currentDate = new Date();
                                String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                User checkedUser = convertJsonToUser(jedis.get(userKey));
                                Date userDate = checkedUser.getLastTimePressedDeposit();
                                if (userDate == null) {
                                    checkedUser.setLastTimePressedDeposit(currentDate);
                                    String updatedUser = convertUserToJson(checkedUser);
                                    jedis.set(userKey, updatedUser);
                                    String sendAdminUID = checkedUser.getUID();
                                    bot.execute(new SendMessage(Long.valueOf(AdminID), "User with Telegram ID<code>" + playerId + "</code> and UID <code>" + sendAdminUID + "</code> \uD83D\uDFE1 deposited. Write 'Y11111111' (telegram id) to approve and 'N1111111' to disapprove").parseMode(HTML));
                                    bot.execute(new SendMessage(playerId, "Awesome! Your deposit will be checked shortly. \uD83C\uDF89\uD83D\uDC4D"));
                                } else {
                                    if (userDate.getTime() <= currentDate.getTime()) {
                                        String sendAdminUID = checkedUser.getUID();
                                        bot.execute(new SendMessage(Long.valueOf(AdminID), "User with Telegram ID<code>" + playerId + "</code> and UID <code>" + sendAdminUID + "</code> \uD83D\uDFE1 deposited. Write 'Y11111111' (telegram id) to approve and 'N1111111' to disapprove").parseMode(HTML));
                                        bot.execute(new SendMessage(playerId, "\uD83D\uDCE9 Awesome! Your deposit will be checked shortly. \uD83C\uDF89\uD83D\uDC4D"));
                                    } else {
                                        bot.execute(new SendMessage(playerId, "\uD83D\uDCE9 Please wait 30 minutes before next time pressing button."));
                                    }
                                }

                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                                e.printStackTrace();
                            }
                        } else if (userDeposited(playerId)) {
                            bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again. "));
                        } else if (messageText.startsWith("/") || messageText.equals("Get Signal")) {
                            bot.execute(new SendMessage(playerId, "Before you can give any signals a try, you'll need to make a deposit first. \uD83D\uDCB0\uD83E\uDD1D"));
                        }
                    } else {
                        if (messageText.equals("/register") || messageCallbackText.equals("RegisterMe")) {
                            InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                            InlineKeyboardButton button2 = new InlineKeyboardButton("Click me and send UID below!");
                            button2.url("https://bit.ly/SiriTradeBot");
                            inlineKeyboardMarkup.addRow(button2);
                            bot.execute(new SendMessage(playerId, "\uD83D\uDE80\uD83D\uDCC8  Well done! To begin, you should create a fresh account on the Quotex platform using the button below. \n" +
                                    "\n" +
                                    " \uD83D\uDD17 bit.ly/SiriTradeBot \n" +
                                    "\n" +
                                    "\uD83D\uDCD7 After registering, send me your ID in format <i>ID12345678</i>\n" +
                                    "\n" +
                                    "\uD83E\uDD16\uD83D\uDD17 Make sure to register using the button below or the link in the message. " +
                                    "Otherwise, we won't be able to verify that you've joined the team.  \n" +
                                    "\n" +
                                    "‼️ Please keep in mind that if you already have an existing Quotex account, " +
                                    "you can delete it and create a new one. Afterward, you can go through the personality " +
                                    "verification process again in your new account. This procedure of deleting and creating a new account is " +
                                    "authorized and allowed by Quotex administrators. \uD83D\uDD04\uD83D\uDCCB")
                                    .replyMarkup(inlineKeyboardMarkup)
                                    .parseMode(HTML).disableWebPagePreview(true));
                            bot.execute(new SendVideo(playerId, videoRegistrationFile));
                            bot.execute(new SendMessage(playerId, "☝️ Here is a video guide on how to register.").parseMode(HTML));
                        } else if (messageCallbackText.equals("ImRegistered")) {
                            bot.execute(new SendMessage(playerId, "\uD83C\uDD94\uD83D\uDCEC Okay! Now, please send me your Quotex ID in the format <i>ID12345678</i>. ").parseMode(HTML));
                        } else if (messageCallbackText.equals("YesIM")) {
                            String userKey = USER_DB_MAP_KEY + ":" + playerId;
                            try {
                                User user = convertJsonToUser(jedis.get(userKey));
                                String sendAdminUID = user.getUID();
                                User adminUser = convertJsonToUser(jedis.get(AdminID));
                                if (Integer.parseInt(sendAdminUID.substring(0, 2)) >= Integer.parseInt(adminUser.getUID())) {
                                    bot.execute(new SendMessage(Long.valueOf(AdminID), "User with Telegram ID<code>" + playerId + "</code> and UID <code>" + sendAdminUID + "</code> \uD83D\uDFE2 want to register. Write 'A11111111' (telegram id) to approve and 'D1111111' to disapprove").parseMode(HTML));
                                    bot.execute(new SendMessage(playerId, "\uD83C\uDF89\uD83D\uDC4D Awesome! Your ID will be checked shortly."));
                                } else {
                                    InlineKeyboardButton button12 = new InlineKeyboardButton("Register here");
                                    InlineKeyboardButton button13 = new InlineKeyboardButton("Registered!");
                                    button12.url("bit.ly/SiriTradeBot");
                                    button13.callbackData("ImRegistered");
                                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                    inlineKeyboardMarkup.addRow(button12, button13);
                                    bot.execute(new SendMessage(playerId, "❌ Your ID is invalid. Please make sure you registered using the 'Register here' button and sent a new UID. After registering, press 'Registered!' again. \uD83D\uDE4F\uD83D\uDD0D\uD83D\uDD04\n" +
                                            "\n" +
                                            "\uD83C\uDD98\uD83D\uDCDE\uD83D\uDC65 If you're still facing issues, please contact support by using the command /help. They'll be able to assist you further.").replyMarkup(inlineKeyboardMarkup));
                                }
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please send your ID again.  "));
                                e.printStackTrace();
                            }
                        } else if (!messageText.startsWith("/")) {
                            try {
                                Pattern pattern = Pattern.compile("\\d{8}");
                                Matcher matcher = pattern.matcher(messageText);
                                if (matcher.find()) {
                                    uid = matcher.group();
                                    InlineKeyboardMarkup inlineKeyboardMarkup = new InlineKeyboardMarkup();
                                    InlineKeyboardButton button5 = new InlineKeyboardButton("Yes");
                                    InlineKeyboardButton button6 = new InlineKeyboardButton("No");
                                    button5.callbackData("YesIM");
                                    button6.callbackData("ImRegistered");
                                    inlineKeyboardMarkup.addRow(button5, button6);
                                    Date date = new Date();
                                    Date depositDate = DateUtil.addDays(date, -1);
                                    User newUser = new User(playerName, uid, false, false, date, depositDate, 1, true, true, true, 1, 50, 0, 0);
                                    bot.execute(new SendMessage(playerId, "\uD83D\uDCCC Is your ID " + uid + " correct? ✅\uD83C\uDD94").replyMarkup(inlineKeyboardMarkup).parseMode(HTML));
                                    String userKey = USER_DB_MAP_KEY + ":" + playerId;
                                    jedis.set(userKey, convertUserToJson(newUser));
                                } else {
                                    bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again.  "));
                                }
                            } catch (Exception e) {
                                bot.execute(new SendMessage(playerId, "❌ There was an issue. Please try again.  "));
                                e.printStackTrace();
                            }
                        }  else if (messageText.equals("Get Signal")) {
                            bot.execute(new SendMessage(playerId, "Before you can try any signals, it's essential to complete the registration process. \uD83D\uDCDD\uD83D\uDD10"));
                        }
                    }
                });

            } catch (Exception e) {
                e.printStackTrace();
            }

            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            jedisPool.close();
        }));
    }


}
