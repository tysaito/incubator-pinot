/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.pinot.thirdeye.detection.alert.scheme;

import com.google.common.base.Preconditions;
import org.apache.pinot.thirdeye.alert.commons.EmailContentFormatterFactory;
import org.apache.pinot.thirdeye.alert.commons.EmailEntity;
import org.apache.pinot.thirdeye.alert.content.EmailContentFormatter;
import org.apache.pinot.thirdeye.alert.content.EmailContentFormatterConfiguration;
import org.apache.pinot.thirdeye.alert.content.EmailContentFormatterContext;
import org.apache.pinot.thirdeye.anomaly.SmtpConfiguration;
import org.apache.pinot.thirdeye.anomaly.ThirdEyeAnomalyConfiguration;
import org.apache.pinot.thirdeye.anomalydetection.context.AnomalyResult;
import org.apache.pinot.thirdeye.datalayer.dto.AlertConfigDTO;
import org.apache.pinot.thirdeye.datalayer.dto.DetectionAlertConfigDTO;
import org.apache.pinot.thirdeye.datalayer.dto.MergedAnomalyResultDTO;
import org.apache.pinot.thirdeye.datalayer.pojo.AlertConfigBean;
import org.apache.pinot.thirdeye.detection.ConfigUtils;
import org.apache.pinot.thirdeye.detection.alert.AlertUtils;
import org.apache.pinot.thirdeye.detection.alert.DetectionAlertFilterRecipients;
import org.apache.pinot.thirdeye.detection.alert.DetectionAlertFilterResult;
import org.apache.pinot.thirdeye.detection.annotation.AlertScheme;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.mail.DefaultAuthenticator;
import org.apache.commons.mail.EmailException;
import org.apache.commons.mail.HtmlEmail;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.apache.pinot.thirdeye.anomaly.SmtpConfiguration.SMTP_CONFIG_KEY;


@AlertScheme(type = "EMAIL")
public class DetectionEmailAlerter extends DetectionAlertScheme {
  private static final Logger LOG = LoggerFactory.getLogger(DetectionEmailAlerter.class);

  private static final Comparator<AnomalyResult> COMPARATOR_DESC =
      (o1, o2) -> -1 * Long.compare(o1.getStartTime(), o2.getStartTime());
  private static final String DEFAULT_EMAIL_FORMATTER_TYPE = "MultipleAnomaliesEmailContentFormatter";
  private static final String ENTITY_REPORT_FORMATTER_TYPE = "EntityContentFormatter";
  private static final String EMAIL_WHITELIST_KEY = "emailWhitelist";
  private static final String PROP_EMAIL_SCHEME = "emailScheme";
  private static final String PROP_EMAIL_TEMPLATE = "template";
  private static final String PROP_EMAIL_SUBJECT_STYLE = "subject";

  private ThirdEyeAnomalyConfiguration teConfig;

  public DetectionEmailAlerter(DetectionAlertConfigDTO config, ThirdEyeAnomalyConfiguration thirdeyeConfig,
      DetectionAlertFilterResult result) throws Exception {
    super(config, result);

    this.teConfig = thirdeyeConfig;
  }

  private Set<String> retainWhitelisted(Set<String> recipients, Collection<String> emailWhitelist) {
    if (recipients != null) {
      recipients.retainAll(emailWhitelist);
    }
    return recipients;
  }

  private void whitelistRecipients(DetectionAlertFilterRecipients recipients) {
    if (recipients != null) {
      List<String> emailWhitelist = ConfigUtils.getList(
          this.teConfig.getAlerterConfiguration().get(SMTP_CONFIG_KEY).get(EMAIL_WHITELIST_KEY));
      if (!emailWhitelist.isEmpty()) {
        recipients.setTo(retainWhitelisted(recipients.getTo(), emailWhitelist));
        recipients.setCc(retainWhitelisted(recipients.getCc(), emailWhitelist));
        recipients.setBcc(retainWhitelisted(recipients.getBcc(), emailWhitelist));
      }
    }
  }

  private void validateAlert(DetectionAlertFilterRecipients recipients, Set<MergedAnomalyResultDTO> anomalies) {
    Preconditions.checkNotNull(recipients);
    Preconditions.checkNotNull(anomalies);
    if (recipients.getTo() == null || recipients.getTo().isEmpty()) {
      throw new IllegalArgumentException("Email doesn't have any valid (whitelisted) recipients.");
    }
    if (anomalies.size() == 0) {
      throw new IllegalArgumentException("Zero anomalies found");
    }
  }

  /** Sends email according to the provided config. */
  private void sendEmail(EmailEntity entity) throws EmailException {
    HtmlEmail email = entity.getContent();
    SmtpConfiguration config = SmtpConfiguration.createFromProperties(this.teConfig.getAlerterConfiguration().get(SMTP_CONFIG_KEY));

    if (config == null) {
      LOG.error("No email configuration available. Skipping.");
      return;
    }

    email.setHostName(config.getSmtpHost());
    email.setSmtpPort(config.getSmtpPort());
    if (config.getSmtpUser() != null && config.getSmtpPassword() != null) {
      email.setAuthenticator(new DefaultAuthenticator(config.getSmtpUser(), config.getSmtpPassword()));
      email.setSSLOnConnect(true);
    }
    email.send();

    int recipientCount = email.getToAddresses().size() + email.getCcAddresses().size() + email.getBccAddresses().size();
    LOG.info("Email sent with subject '{}' to {} recipients", email.getSubject(), recipientCount);
  }

  public enum EmailTemplate {
    DEFAULT_EMAIL,
    ENTITY_REPORT
  }

  /**
   * Plug the appropriate template based on configuration.
   */
  private static EmailContentFormatter makeTemplate(Map<String, Object> emailParams) throws Exception {
    EmailTemplate template = EmailTemplate.DEFAULT_EMAIL;
    if (emailParams != null && emailParams.containsKey(PROP_EMAIL_TEMPLATE)) {
      template = EmailTemplate.valueOf(emailParams.get(PROP_EMAIL_TEMPLATE).toString());
    }

    switch (template) {
      case DEFAULT_EMAIL:
        LOG.info("Using the " + DEFAULT_EMAIL_FORMATTER_TYPE + " email template.");
        return EmailContentFormatterFactory.fromClassName(DEFAULT_EMAIL_FORMATTER_TYPE);

      case ENTITY_REPORT:
        LOG.info("Using the " + template + " email template.");
        return EmailContentFormatterFactory.fromClassName(ENTITY_REPORT_FORMATTER_TYPE);

      default:
        throw new IllegalArgumentException(String.format("Unknown type '%s'", template));
    }
  }

  /**
   * Plug the appropriate email subject style based on configuration
   */
  private AlertConfigBean.SubjectType makeSubject(Map<String, Object> emailParams) {
    AlertConfigBean.SubjectType subjectType;
    if (emailParams != null && emailParams.containsKey(PROP_EMAIL_SUBJECT_STYLE)) {
      subjectType = AlertConfigBean.SubjectType.valueOf(emailParams.get(PROP_EMAIL_SUBJECT_STYLE).toString());
    } else {
      // To support the legacy email subject configuration
      subjectType = this.config.getSubjectType();
    }

    return subjectType;
  }

  private void sendEmail(DetectionAlertFilterRecipients recipients, Set<MergedAnomalyResultDTO> anomalies) throws Exception {
    whitelistRecipients(recipients);
    validateAlert(recipients, anomalies);

    Map<String, Object> emailParams = ConfigUtils.getMap(this.config.getAlertSchemes().get(PROP_EMAIL_SCHEME));
    EmailContentFormatter emailContentFormatter = makeTemplate(emailParams);
    emailContentFormatter.init(new Properties(),
        EmailContentFormatterConfiguration.fromThirdEyeAnomalyConfiguration(this.teConfig));

    List<AnomalyResult> anomalyResultListOfGroup = new ArrayList<>(anomalies);
    anomalyResultListOfGroup.sort(COMPARATOR_DESC);

    AlertConfigDTO alertConfig = new AlertConfigDTO();
    alertConfig.setName(this.config.getName());
    alertConfig.setFromAddress(this.config.getFrom());
    alertConfig.setSubjectType(makeSubject(emailParams));
    alertConfig.setReferenceLinks(this.config.getReferenceLinks());

    EmailEntity emailEntity = emailContentFormatter.getEmailEntity(alertConfig, null,
        "Thirdeye Alert : " + this.config.getName(), null, null, anomalyResultListOfGroup,
        new EmailContentFormatterContext());

    HtmlEmail email = emailEntity.getContent();
    email.setFrom(this.config.getFrom());
    email.setTo(AlertUtils.toAddress(recipients.getTo()));
    email.setSubject(emailEntity.getSubject());
    if (CollectionUtils.isNotEmpty(recipients.getCc())) {
      email.setCc(AlertUtils.toAddress(recipients.getCc()));
    }
    if (CollectionUtils.isNotEmpty(recipients.getBcc())) {
      email.setBcc(AlertUtils.toAddress(recipients.getBcc()));
    }

    sendEmail(emailEntity);
  }

  private void generateAndSendEmails(DetectionAlertFilterResult detectionResult) throws Exception {
    LOG.info("Sending Email alert for {}", config.getId());
    Preconditions.checkNotNull(detectionResult.getResult());
    for (Map.Entry<DetectionAlertFilterRecipients, Set<MergedAnomalyResultDTO>> entry : detectionResult.getResult().entrySet()) {
      DetectionAlertFilterRecipients recipients = entry.getKey();
      Set<MergedAnomalyResultDTO> anomalies = entry.getValue();

      try {
        sendEmail(recipients, anomalies);
      } catch (IllegalArgumentException e) {
        LOG.warn("Skipping! Found illegal arguments while sending {} anomalies to recipient {} for alert {}."
            + " Exception message: ", anomalies.size(), recipients, config.getId(), e);
      }
    }
  }

  @Override
  public void run() throws Exception {
    Preconditions.checkNotNull(result);
    if (result.getAllAnomalies().size() == 0) {
      LOG.info("Zero anomalies found, skipping email alert for {}", config.getId());
      return;
    }

    generateAndSendEmails(result);
  }
}
