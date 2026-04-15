const { NotificationTypes } = require('./notificationTypes');

const NotificationTemplates = Object.freeze({
  [NotificationTypes.ANNOUNCEMENT]: {
    i18nKey: 'notifications.announcement',
    title: {
      en: 'School Announcement',
      es: 'Anuncio escolar',
    },
    body: {
      en: '{{message}}',
      es: '{{message}}',
    },
  },
  [NotificationTypes.HOMEWORK]: {
    i18nKey: 'notifications.homework',
    title: {
      en: 'Homework Update',
      es: 'Actualización de tarea',
    },
    body: {
      en: '{{subject}} homework is due on {{dueDate}}',
      es: 'La tarea de {{subject}} vence el {{dueDate}}',
    },
  },
  [NotificationTypes.RESULTS]: {
    i18nKey: 'notifications.results',
    title: {
      en: 'Result Published',
      es: 'Resultado publicado',
    },
    body: {
      en: '{{examName}} results are now available',
      es: 'Los resultados de {{examName}} ya están disponibles',
    },
  },
  [NotificationTypes.ATTENDANCE_ALERT]: {
    i18nKey: 'notifications.attendance',
    title: {
      en: 'Attendance Alert',
      es: 'Alerta de asistencia',
    },
    body: {
      en: '{{studentName}} marked {{status}} on {{date}}',
      es: '{{studentName}} marcado como {{status}} el {{date}}',
    },
  },
  [NotificationTypes.FEE_REMINDER]: {
    i18nKey: 'notifications.fee',
    title: {
      en: 'Fee Reminder',
      es: 'Recordatorio de cuota',
    },
    body: {
      en: '{{feeName}} payment due on {{dueDate}}',
      es: 'El pago de {{feeName}} vence el {{dueDate}}',
    },
  },
});

function interpolate(template, context = {}) {
  return Object.entries(context).reduce((result, [key, value]) => {
    const nextValue = value == null ? '' : String(value);
    return result.replaceAll(`{{${key}}}`, nextValue);
  }, template);
}

function buildLocalizedPayload({ type, locale = 'en', context = {}, overrides = {} }) {
  const template = NotificationTemplates[type];
  if (!template) {
    throw new Error(`Unsupported notification type: ${type}`);
  }

  const resolvedLocale = template.title[locale] ? locale : 'en';
  return {
    type,
    locale: resolvedLocale,
    i18nKey: template.i18nKey,
    title: overrides.title || interpolate(template.title[resolvedLocale], context),
    body: overrides.body || interpolate(template.body[resolvedLocale], context),
  };
}

module.exports = {
  NotificationTemplates,
  buildLocalizedPayload,
};
