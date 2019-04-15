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

    // Setup category table
    $('.category-table').find('tr').click(function() {
        var categoryString = '';
        var id = $(this).data('id');
        if ($(this).hasClass('selected')) {
            // Category is already selected
            var self = $(this);
            var selected = $('.category-table').find('.selected');
            selected.each(function(i) {
                if ($(this).is(self)) return; // Skip the clicked category
                categoryString += $(this).data('id');
                if (i < selected.length - 1) categoryString += ',';
            });
        } else if (CATEGORY_STRING) {
            categoryString += CATEGORY_STRING + ',' + $(this).data('id');
        } else {
            categoryString += id;
        }

        // Build URL
        var url = '/?';
        if (categoryString.length > 0) {
            url += 'categories=' + categoryString;
            if (SORT_STRING) url += '&sort=' + SORT_STRING;
            if (ORDER_WITH_RELEVANCE) url += '&relevance=' + ORDER_WITH_RELEVANCE;
        } else if (SORT_STRING) {
            url += 'sort=' + SORT_STRING;
            if (ORDER_WITH_RELEVANCE) url += '&relevance=' + ORDER_WITH_RELEVANCE;
        } else if (ORDER_WITH_RELEVANCE) {
            url += 'relevance=' + ORDER_WITH_RELEVANCE;
        }

        // Fly you fools!
        go(url);
    });

    // Initialize sorting selection
    $('.select-sort').on('change', function() {
        var url = '/?sort=' + $(this).find('option:selected').val();
        if (QUERY_STRING) url += '&q=' + QUERY_STRING;
        if (CATEGORY_STRING) url += '&categories=' + CATEGORY_STRING;
        if (ORDER_WITH_RELEVANCE) url += '&relevance=' + ORDER_WITH_RELEVANCE;
        go(url);
    });

    $('#relevanceBox').change(function() {
        var url = '/?relevance=' + this.checked;
        if (QUERY_STRING) url += '&q=' + QUERY_STRING;
        if (CATEGORY_STRING) url += '&categories=' + CATEGORY_STRING;
        if (SORT_STRING) url += '&sort=' + SORT_STRING;
        go(url);
    });
});
