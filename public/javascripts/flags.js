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
 * Handles flag management in admin panel
 *
 * ==================================================
 */

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.btn-resolve').click(function() {
        var listItem = $(this).closest('li');
        var flagId = listItem.data('flag-id');
        var spinner = toggleSpinner($(this).find('[data-fa-i2svg]').removeClass('fa-check'));

        $.ajax({
            url: '/admin/flags/' + flagId + '/resolve/true',
            complete: function() { toggleSpinner($('.btn-resolve').find('[data-fa-i2svg]').addClass('fa-check')); },
            success: function() {
                $.when(listItem.fadeOut('slow')).done(function() {
                    listItem.remove();
                    if (!$('.list-flags-admin').find('li').length) {
                        resolveAll.fadeOut();
                        $('.no-flags').fadeIn();
                        clearUnread($('a[href="/admin/flags"]'));
                    }
                });
            }
        });
    });
});
