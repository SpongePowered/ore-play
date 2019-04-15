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
 * Powers the admin reviews.
 *
 * ==================================================
 */


/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.btn-review-start').click(function() {
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-terminal'));
        $(this).attr("disabled", "disabled");
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/init',
            data: { csrfToken: csrf },
            complete: function() { toggleSpinner($('.btn-review-start [data-fa-i2svg]').addClass('fa-terminal')); },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-skip-review').click(function() {
        var btn = $(this);
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/reviewtoggle',
            data: { csrfToken: csrf },
            complete: function() { btn.html('Add to queue'); },
            success: function() {
                location.reload();
            }
        })
    });

    $('.btn-review-stop').click(function () {
        var modal = $('#modal-review-stop');
        modal.modal().show();
        modal.on('shown.bs.modal', function() { $(this).find('textarea').focus(); });
    });

    $('.btn-review-stop-submit').click(function() {
        var icon = toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-times-circle-o'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/stop',

            data: { csrfToken: csrf, content: $('.textarea-stop').val() },
            complete: function() { toggleSpinner(icon.addClass('fa-times-circle-o')); },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-review-approve').click(function() {
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-thumbs-up'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/approve',
            data: { csrfToken: csrf }
        });
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/approve',
            data: { csrfToken: csrf },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-review-approve-partial').click(function() {
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-thumbs-up'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/approve',
            data: { csrfToken: csrf }
        });
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/approvePartial',
            data: { csrfToken: csrf },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-review-takeover').click(function () {
        var modal = $('#modal-review-takeover');
        modal.modal().show();
        modal.on('shown.bs.modal', function() { $(this).find('textarea').focus(); });
    });

    $('.btn-review-takeover-submit').click(function() {
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-clipboard'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/takeover',
            data: { csrfToken: csrf, content: $('.textarea-takeover').val() },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-edit-message').click(function () {
        var panel = $(this).parent().parent().parent();
        var text = panel.find('textarea');
        text.attr('disabled', null);
        $(this).hide();
        panel.find('.btn-cancel-message').show();
        panel.find('.btn-save-message').show();
    });

    $('.btn-cancel-message').click(function(){
        location.reload();
    });

    $('.btn-save-message').click(function() {
        var panel = $(this).parent().parent().parent();
        var textarea = panel.find('textarea');
        textarea.attr('disabled', 'disabled');
        toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-save'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/edit/' + panel.data('review'),
            data: { csrfToken: csrf, content: textarea.val() },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-review-addmessage-submit').click(function() {
        toggleSpinner($(this).find('[data-fa-i2svg]').removeClass('fa-clipboard'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/addmessage',
            data: { csrfToken: csrf, content: $('.textarea-addmessage').val() },
            success: function() {
                location.reload();
            }
        });
    });

    $('.btn-review-reopen').click(function() {
        var icon = toggleSpinner($(this).find('[data-fa-i2svg]').toggleClass('fa-terminal'));
        $(this).attr("disabled", "disabled");
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/reviews/reopen',
            data: { csrfToken: csrf },
            complete: function() { toggleSpinner(icon.toggleClass('fa-terminal')); },
            success: function() {
                location.reload();
            }
        });
    });
});
