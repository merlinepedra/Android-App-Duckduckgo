import i18next from 'i18next'
import ICU from 'i18next-icu'
const supportedLocales = require('./../../../data/constants.js').supportedLocales

function buildLocalesList (locales) {
    const resources = {}
    // hard coding for now till we figure out dynamic bundling
    resources.en = {
        shared: require('../../../locales/en/shared.json'),
        site: require('../../../locales/en/site.json'),
        connection: require('../../../locales/en/connection.json'),
        report: require('../../../locales/en/report.json'),
        permissions: require('../../../locales/en/permissions.json')
    }
    resources.pl = {
        shared: require('../../../locales/pl/shared.json'),
        site: require('../../../locales/pl/site.json'),
        connection: require('../../../locales/pl/connection.json'),
        report: require('../../../locales/pl/report.json'),
        permissions: require('../../../locales/pl/permissions.json')
    }
    return resources
}

i18next
    .use(ICU)
    .init({
    // debug: true,
        initImmediate: false,
        fallbackLng: 'en',
        lng: 'en',
        ns: ['shared', 'site', 'connection', 'report'],
        defaultNS: 'shared',
        resources: buildLocalesList(supportedLocales)
    })

module.exports = i18next
