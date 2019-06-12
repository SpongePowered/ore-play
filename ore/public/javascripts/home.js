//=====> EXTERNAL CONSTANTS

var CATEGORY_STRING = null;
var SORT_STRING = null;
var QUERY_STRING = null;
var ORDER_WITH_RELEVANCE = null;

var NUM_SUFFIXES = ["", "k", "m"];
var currentlyLoaded = 0;


//=====> HELPER FUNCTIONS

function abbreviateStat(stat) {
    stat = stat.toString().trim();
    if (parseInt(stat) < 1000) return stat;
    var suffix = NUM_SUFFIXES[Math.min(2, Math.floor(stat.length / 3))];
    return stat[0] + '.' + stat[1] + suffix;
}


//=====> DOCUMENT READY

$(function() {
    $('.project-table').find('tbody').find('.stat').each(function() {
        $(this).text(abbreviateStat($(this).text()));
    });

    $('.dismiss').click(function() {
        $('.search-header').fadeOut('slow');
        var url = '/';
        if (CATEGORY_STRING || SORT_STRING || ORDER_WITH_RELEVANCE)
            url += '?';
        if (CATEGORY_STRING)
            url += 'categories=' + CATEGORY_STRING;
        if (SORT_STRING) {
            if (CATEGORY_STRING)
                url += '&';
            url += '&sort=' + SORT_STRING;
        }
        if (ORDER_WITH_RELEVANCE) {
            if (CATEGORY_STRING || SORT_STRING)
                url += '&';
            url += '&relevance=' + ORDER_WITH_RELEVANCE;
        }
        go(url);
    });
});
