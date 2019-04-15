/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * By Walker Crouse (windy) and contributors
 * (C) SpongePowered 2016-2017 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Handles channel creation that occurs during version creation (separate from
 * the normal channel manager).
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var DEFAULT_COLOR = null;

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function initChannelNew(color) {
    initChannelManager(
        '#channel-new', '', color, 'New channel', null, null,
        'Create channel', false
    );
}

function getForm() {
    return $('#form-publish');
}

function getSelect() {
    return $('#select-channel');
}

function setColorInput(val) {
    getForm().find('.channel-color-input').val(val);
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    initChannelNew(DEFAULT_COLOR);

    getSelect().change(function() {
        setColorInput($(this).find(':selected').data('color'));
    });

    onCustomSubmit = function(toggle, channelName, channelHex, title, submit, nonReviewed) {
        // Add new name to select
        var select = getSelect();
        var exists = select.find('option').find(function() {
            return $(this).val().toLowerCase() === channelName.toLowerCase();
        }).length !== 0;

        if (!exists) {
            setColorInput(channelHex);
            select.find(':selected').removeAttr('selected');
            select.append('<option data-color="' + channelHex + '" '
                                + 'value="' + channelName + '" '
                                + 'selected>' + channelName + '</option>');
        }

        $('#channel-manage').modal('hide');
        initChannelNew(DEFAULT_COLOR);
    }
});
