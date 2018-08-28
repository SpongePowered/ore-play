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
 * =                Helper functions                =
 * ==================================================
 */

function addSpinner(e) {
    return e.addClass('fa-spinner fa-spin');
}

function removeSpinner(e) {
    return e.removeClass('fa-spinner fa-spin');
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.btn-resolve').click(function() {
        var listItem = $(this).closest('li');
        var flagId = listItem.data('flag-id');
        var spinner = addSpinner($(this).find('i').removeClass('fa-check'));
        $.ajax({
            url: '/admin/flags/' + flagId + '/resolve/true',
            complete: function() { removeSpinner(spinner.addClass('fa-check')); },
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
