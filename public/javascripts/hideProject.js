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
 * Hides projects in Ore via AJAX
 *
 * ==================================================
 */

var ICON = 'fa-eye';

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.btn-visibility-change').click(function () {
        var project = $(this).data('project');
        var visibilityLevel = $(this).data('level');
        var needsModal = $(this).data('modal');
        var spinner = $('button[data-project="'  + project + '"]').find('i');
        spinner.removeClass(ICON).addClass('fa-spinner fa-spin');
        if (needsModal) {
            $('.modal-title').html($(this).text().trim() + ": comment");
            $('#modal-visibility-comment').modal('show');
            $('.btn-visibility-comment-submit').data('project', project);
            $('.btn-visibility-comment-submit').data('level', visibilityLevel);
            spinner.addClass(ICON).removeClass('fa-spinner fa-spin');
        } else {
            sendVisibilityRequest(project, visibilityLevel, '', spinner);
        }
    });

    $('.btn-visibility-comment-submit').click(function () {
        var project = $(this).data('project');
        var visibilityLevel = $(this).data('level');
        var spinner = $(this).find('i');
        spinner.removeClass(ICON).addClass('fa-spinner fa-spin');
        sendVisibilityRequest(project, visibilityLevel, $('.textarea-visibility-comment').val(), spinner);

    });

    function sendVisibilityRequest(project, level, comment, spinner) {
        var _url = '/' + project + (level == -99 ? '/manage/hardDelete' : '/visible/' + level);
        $.ajax({
            type: 'post',
            url: _url,
            data: { csrfToken: csrf, comment: comment },
            fail: function () {
                spinner.addClass(ICON).removeClass('fa-spinner fa-spin');
            },
            success: function () {
                spinner.addClass(ICON).removeClass('fa-spinner fa-spin');
                location.reload();
            }
        });
    }
});
