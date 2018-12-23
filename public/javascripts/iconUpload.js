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
 * Uploads project icons via AJAX
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var PROJECT_OWNER = null;
var PROJECT_SLUG = null;

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    var form = $('#form-icon');
    var btn = form.find('.btn-upload');
    var url = sanitize('/' + PROJECT_OWNER + '/' + PROJECT_SLUG + '/icon');
    var preview = form.find('.user-avatar');
    var input = form.find('input[type="file"]');

    function updateButton() {
        btn.prop('disabled', input[0].files.length === 0);
    }

    input.on('change', function() { updateButton(); });

    var formData = new FormData(form[0]);
    formData.append('csrfToken', csrf);

    // Upload button
    btn.click(function(e) {
        e.preventDefault();
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-upload'));
        $.ajax({
            url: url,
            type: 'post',
            data: new FormData(form[0]),
            cache: false,
            contentType: false,
            processData: false,
            success: function() {
                preview.css('background-image', 'url(' + url + '/pending' + ')');
                toggleSpinner($('#form-icon .btn-upload').find('[data-fa-i2svg]').toggleClass('fa-upload'));
                $('#update-icon').val('true');
                input.val('');
                updateButton();
                $('.setting-icon').prepend('<div class="alert alert-info">Don\'t forget to save changes!</div>');
            }
        });
    });

    // Reset button
    var reset = form.find('.btn-reset');
    reset.click(function(e) {
        e.preventDefault();
        $(this).text('').append('<i class="fas fa-spinner fa-spin"></i>');
        $.ajax({
            url: url + '/reset',
            type: 'post',
            cache: false,
            contentType: 'application/json',
            complete: function() {
                reset.empty().text('Reset');
            },
            success: function() {
                preview.css('background-image', 'url(' + url + ')');
                input.val('');
                updateButton();
                $('.setting-icon .alert').detach();
            }
        });
    });
});
