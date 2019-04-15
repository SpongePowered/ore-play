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
 * Powers the admin approval queue.
 *
 * ==================================================
 */

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.btn-approve').click(function() {
        var listItem = $(this).closest('.list-group-item');
        var versionPath = listItem.data('version');
        var icon = toggleSpinner($(this).find('[data-fa-i2svg]').removeClass('fa-thumbs-up'));
        $.ajax({
            type: 'post',
            url: '/' + versionPath + '/approve',
            data: { csrfToken: csrf },
            complete: function() { toggleSpinner($('.btn-approve').find('[data-fa-i2svg]').addClass('fa-thumbs-up')); },
            success: function() {
                $.when(listItem.fadeOut('slow')).done(function() { 
                    listItem.remove();
                    if (!$('.list-versions').find('li').length) {
                        $('.no-versions').fadeIn();
                        clearUnread($('a[href="/admin/queue"]'));
                    }
                });
            }
        });
    });
});
