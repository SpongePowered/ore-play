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
 * Handles sorting/management of authors table @ /authors
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var CURRENT_PAGE = 0;

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function() {
    $('.table-users').find('thead').find('td:not(:first-child)').click(function() {
        var sort = $(this).text().toLowerCase().trim();
        var direction = '';
        var thisObj = $(this);
        if (thisObj.hasClass('user-sort')) {
            // Change direction
            direction = $(this).find('svg').hasClass('fa-chevron-up') ? '-' : '';
        }
        var start = thisObj.data("list");
        var url = '/' + start + '?sort=' + direction + sort;
        if (CURRENT_PAGE > 1) url += '&page=' + CURRENT_PAGE;
        go(url);
    });
});
