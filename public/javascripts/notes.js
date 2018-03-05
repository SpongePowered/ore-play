/*
 * ==================================================
 *  _____             _
 * |     |___ ___    |_|___
 * |  |  |  _| -_|_  | |_ -|
 * |_____|_| |___|_|_| |___|
 *                 |___|
 *
 * (C) SpongePowered 2018 MIT License
 * https://github.com/SpongePowered/Ore
 *
 * Powers the notes.
 *
 * ==================================================
 */


/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.btn-note-addmessage-submit').click(function() {
        var panel = $(this).parent().parent().parent();
        var textarea = panel.find('textarea');
        textarea.attr('disabled', 'disabled');
        $(this).find('i').removeClass('fa-save').addClass('fa-spinner fa-spin');
        $.ajax({
            type: 'post',
            url: '/' + resourcePath + '/notes/addmessage',
            data: { csrfToken: csrf, content: textarea.val() },
            success: function() {
                location.reload();
            }
        });
    });
});
