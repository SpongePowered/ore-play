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
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-save'));
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
