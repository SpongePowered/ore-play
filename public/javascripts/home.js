//=> Nonce Variables

var CATEGORY_STRING = null;
var SORT_STRING = null;
var QUERY_STRING = null;
var ORDER_WITH_RELEVANCE = null;

var PLATFORM_CATEGORY = null;
var PLATFORM = null;

//=> jQuery Doc Ready

$(function() {
    $('.select-sort').on('change', function() {
        var sort = $(this).find('option:selected').val();
        go(jsRoutes.controllers.Application.showHome(CATEGORY_STRING, QUERY_STRING, sort, null, PLATFORM_CATEGORY, CATEGORY_STRING, ORDER_WITH_RELEVANCE).absoluteURL);
    });

    $('#relevanceBox').on('change', function() {
        go(jsRoutes.controllers.Application.showHome(CATEGORY_STRING, QUERY_STRING, SORT_STRING, null, PLATFORM_CATEGORY, CATEGORY_STRING, this.checked).absoluteURL);
    });

    var projectSearch = $('.project-search');
    projectSearch.find('input').on('keypress', function(event) {
        if (event.keyCode === KEY_ENTER) {
            event.preventDefault();
            $(this).next().find('.btn').click();
        }
    });

    projectSearch.find('.btn').click(function() {
        var query = $(this).closest('.input-group').find('input').val();
        go(jsRoutes.controllers.Application.showHome(CATEGORY_STRING, query, SORT_STRING, null, PLATFORM_CATEGORY, CATEGORY_STRING, ORDER_WITH_RELEVANCE).absoluteURL);
    });
});
