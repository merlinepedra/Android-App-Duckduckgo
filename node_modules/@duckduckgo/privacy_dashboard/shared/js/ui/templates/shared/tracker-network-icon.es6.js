const bel = require('bel')
const { isSiteWithOnlyOwnTrackers } = require('./utils.es6.js')

module.exports = function (site) {
    const { isWhitelisted, totalTrackerNetworksCount } = site

    let iconNameModifier = 'blocked'

    if (isWhitelisted && (totalTrackerNetworksCount !== 0)) {
        iconNameModifier = 'warning'
    }

    if (isSiteWithOnlyOwnTrackers(site)) {
        iconNameModifier = 'mixed'
    }

    const iconName = 'major-networks-' + iconNameModifier

    return bel`${iconName}`
}
