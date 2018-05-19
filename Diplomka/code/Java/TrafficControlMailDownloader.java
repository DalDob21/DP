package cz.sa.ybus.server.infrastructure.provider.trafficcontrol;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import java.util.regex.Pattern;

import javax.annotation.Nullable;
import javax.mail.Flags;
import javax.mail.Flags.Flag;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.Part;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeUtility;
import javax.mail.search.FlagTerm;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import cz.sa.ybus.impl.common.general.Config;
import cz.sa.ybus.impl.common.general.ConfigKeys;

/**
 * Trida napsana namiru nacitani struktury vlaku z excelu, po vytvoreni dispecerskeho systemu bude jiz nepotrebna a prijde odstranit
 * @author dalibor.dobes
 *
 */
@Component
public class TrafficControlMailDownloader {
  
  @Autowired
  private Config config;

  private static final String HOST = "imap.sa.cz";
  private static final String PORT = "993";
  private static final String PROTOCOL = "imaps";
  private static final Pattern EXCEL_SUFFIX = Pattern.compile("([^\\s]+(\\.(?i)(xlsx))$)");

  private final Logger log = LoggerFactory.getLogger(getClass());

  @Nullable
  public byte[] downloadEmailAttachments() {

    /* prihlasovaci udaje */
    final String userName = config.getProperty(ConfigKeys.TRAFFIC_CONTROL_TRAIN_USERNAME);
    final String password = config.getProperty(ConfigKeys.TRAFFIC_CONTROL_TRAIN_PASSWORD);

    /* nastaveni properties pro session */
    final Properties properties = getConnectionProperties();
    final Session session = Session.getInstance(properties);
    
    byte[] attachmentBytes = null;

    try {
      final Store store = session.getStore(PROTOCOL);

      /* vytvoreni spojeni */
      store.connect(HOST, userName, password);

      /* slozka s e-maily */
      final Folder folderInbox = store.getFolder("INBOX");
      folderInbox.open(Folder.READ_WRITE);

      /* vrati jen neprectene zpravy */
      final Message messages[] = folderInbox.search(new FlagTerm(new Flags(Flag.SEEN), false));
      if (messages.length == 0) {
        log.info("There are no new emails on address ybus.dispecink@studentagency.cz");
        return null;
      }
      boolean manyNewMails = messages.length > 1 ? true : false;
      /* pokud mame vice novych zprav, tak chceme tu s nejnovjejsim datem. Cislovani je od nejstarsi po nejnovjejsi, proto potrebujem obratit radu zprav
       * aby nam prvni index (0) ukazoval na nejnovjejsi */
      if (manyNewMails) {
        ArrayUtils.reverse(messages);
      }
      boolean foundEmailWithExcelAttachment = false;

      for (int i = 0; i < messages.length; i++) {
        /* chceme posledni prijatou zpravu */
        final Message message = messages[i];
        message.setFlag(Flag.SEEN, true);
        /* po nalezeni a precteni nejnovjejsi zpravy potrebujem ostatnim nastavit ze jsou prectene (pokud existuji) */
        if (foundEmailWithExcelAttachment) {
          continue;
        }
        final String contentType = message.getContentType();

        /* mail musi obsahovat prilohy (indikuje se podle "multipart") */
        if (contentType.contains("multipart")) {

          /* po precteni mailu dojde k automatickemu nastaveni na Flag.SEEN "precteno" ("message.getContent()") */
          final Multipart multiPart = (Multipart) message.getContent();
          final int numberOfParts = multiPart.getCount();
          for (int partCount = 0; partCount < numberOfParts; partCount++) {
            final Part part = multiPart.getBodyPart(partCount);
            final String disposition = part.getDisposition();
            
            /* otevru pouze cast mimeBodyPart ve kterem je ulozena priloha */
            if ((disposition != null)
                && ((disposition.equalsIgnoreCase(Part.ATTACHMENT) || (disposition.equalsIgnoreCase(Part.INLINE))))) {
              final MimeBodyPart mimeBodyPart = (MimeBodyPart) part;
              final String fileName =  MimeUtility.decodeText(mimeBodyPart.getFileName());
              /* muze se stat ze nam na mail dojde zprava s nejakou prilohou ale mi potrebujem pouze email s prilohou obsahujici excel */
              if (EXCEL_SUFFIX.matcher(fileName).find()) {
                try (final InputStream is = mimeBodyPart.getInputStream()) {
                  attachmentBytes = IOUtils.toByteArray(is);
                  foundEmailWithExcelAttachment = true;
                }
              }
            }
          }
        }
      }

      folderInbox.close(false);
      store.close();
    }
    catch (MessagingException | IOException ex) {
      log.error("Error while accessing e-mail store or reading e-mail.", ex);
    }
    return attachmentBytes;
  }

  private Properties getConnectionProperties() {

    final Properties properties = new Properties();
    properties.setProperty(String.format("mail.%s.host", PROTOCOL), HOST);
    properties.setProperty(String.format("mail.%s.port", PROTOCOL), PORT);
    properties.setProperty(String.format("mail.store.protocol"), PROTOCOL);
    properties.setProperty(String.format("mail.%s.auth", PROTOCOL), "true");
    properties.setProperty(String.format("mail.%s.socketFactory.class", PROTOCOL), "javax.net.ssl.SSLSocketFactory");
    properties.setProperty(String.format("mail.%s.socketFactory.fallback", PROTOCOL), "false");
    properties.setProperty(String.format("mail.%s.socketFactory.port", PROTOCOL), PORT);

    return properties;
  }
}