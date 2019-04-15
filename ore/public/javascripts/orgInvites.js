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
 * Powers the orga invites page.
 *
 * ==================================================
 */

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function replyToInvite(id, behalf, reply, success, error) {
    var url = '/invite/' + id + '/' + reply + '/' + behalf;
    $.ajax({
        type: 'post',
        url: url,
        data: { csrfToken: csrf },
        success: success,
        error: error
    });
}

function setupInvites() {

    $('.btn-invite').click(function() {
        var btn = $(this);
        var id = btn.attr('data-invite-id');
        var behalf = btn.attr('data-invite-behalf');
        var accepted = btn.attr('data-invite-accepted');
        btn.attr('disabled', true);

        replyToInvite(id, behalf, accepted, function() {
            if (accepted == 'decline') {
                btn.parent().parent().hide();
            } else {
                btn.parent().html('<span>Joined</span>');
            }
        }, function() {
            btn.html('Failed to update');
        });
    });
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    setupInvites();
});
