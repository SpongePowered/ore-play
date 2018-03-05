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
 * Handles async loading and display of the version list.
 *
 * ==================================================
 */

/*
 * ==================================================
 * =               External constants               =
 * ==================================================
 */

var PLUGIN_ID = null;
var CHANNEL_STRING = '';
var VERSIONS_PER_PAGE = 10;
var PROJECT_OWNER = null;
var PROJECT_SLUG = null;
var TOTAL_VERSIONS = 0;

var page = 0;

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function loadVersions(increment, scrollTop) {
    var versionList = $('.version-list');

    var offset = (page + increment - 1) * VERSIONS_PER_PAGE;
    var url = '/api/projects/' + PLUGIN_ID + '/versions?offset=' + offset;
    if (CHANNEL_STRING) url += '&channels=' + CHANNEL_STRING;

    $.ajax({
        url: url,
        dataType: 'json',
        success: function (versions) {
            var content = "";

            versions.forEach(function (version) {
                var channel = version.channel;
                var tags = version.tags;

                var tagsHtml = "";
                tags.forEach(function (tag) {
                    var style = "background:" + tag.backgroundColor +";border-color:" + tag.backgroundColor + ";color:" + tag.foregroundColor;

                    if(tag.data !== "" && tag.data !== "null") {
                        tagsHtml += "<div class='tags has-addons'><span style='" + style + "' class='tag'>" + tag.name + "</span><span class='tag'>" + tag.data + "</span></div>";
                    } else {
                        tagsHtml += "<div class='tags'><span style='" + style + "' class='tag'>" + tag.name + "</span></div>";
                    }
                });

                // Build result row
                var versionTemplate = $('.version-template').clone().removeAttr('id');

                versionTemplate.find('.channel').text(channel.name).css("background", channel.color);
                versionTemplate.find('.name').html("<a href='" + version.href + "'>" + version.name + "</a>");
                versionTemplate.find('.version-tags').html(tagsHtml);
                versionTemplate.find('.information-one .created').text(moment(version.createdAt).format("MMM D, YYYY"));
                versionTemplate.find('.information-one .size').text(filesize(version.fileSize));
                if(version.author != null) {
                    versionTemplate.find('.information-two .author').show();
                    versionTemplate.find('.information-two .author-name').text(version.author);
                }
                versionTemplate.find('.information-two .download-count').text(version.downloads);

                var downloadLink = versionTemplate.find('.download .download-link');
                var tooltip = "";
                downloadLink.attr('href', version.href +  '/download/');

                if (!version.staffApproved) {
                    var text = "This version has not been reviewed by our moderation staff and may not be safe for download!";
                    downloadLink.html(downloadLink.html() + "<i title='" + text + "' data-toggle='tooltip' data-placement='bottom' class='fa fa-exclamation-circle'></i>")
                }

                downloadLink.attr('title', tooltip);

                content += "<tr class='version'>" + versionTemplate.html() + "</tr>";
            });
            versionList.html(content);

            // Sets the new page number
            page += increment;
            var totalPages = Math.ceil(TOTAL_VERSIONS / VERSIONS_PER_PAGE);

            if(totalPages > 1) {

                // Sets up the pagination
                var pagination = $(".version-panel .pagination");

                var left = totalPages - page;
                content = "";

                // [First] ...
                if(totalPages > 3 && page >= 3) {
                    content += "<li class='page'><a>" + 1 + "</a></li>";

                    if(page > 3) {
                        content += "<li class='disabled'><a>...</a></li>"
                    }
                }

                //=> [current - 1] [current] [current + 1] logic
                if(totalPages > 2) {
                    if(left === 0) {
                        content += "<li class='page'><a>" + (totalPages - 2) + "</a></li>" // Adds a third page if current page is last page
                    }
                }

                if(page !== 1) {
                    content += "<li class='page'><a>" + (page - 1) + "</a></li>"
                }

                content += "<li class='page active'><a>" + page + "</a></li>";

                if((page + 1) <= totalPages) {
                    content += "<li class='page'><a>" + (page + 1) + "</a></li>";
                }

                if(totalPages > 2) {
                    if(page === 1) {
                        content += "<li class='page'><a>" + (page + 2) + "</a></li>" // Adds a third page if current page is first page
                    }
                }

                // [Last] ...
                if(totalPages > 3 && left > 1) {
                    if(left > 2) {
                        content += "<li class='disabled'><a>...</a></li>"
                    }

                    content += "<li class='page'><a>" + totalPages + "</a></li>"
                }

                // Builds the pagination
                pagination.html(
                    "<li class='prev" + (page === 1 ? " disabled" : "") + "'><a>&laquo;</a></li>" +
                    content +
                    "<li class='next" + (TOTAL_VERSIONS / VERSIONS_PER_PAGE <= page ? " disabled" : "") + "'><a>&raquo;</a></li>"
                );

                // Prev & Next Buttons
                pagination.find('.next').click(function () {
                    if (TOTAL_VERSIONS / VERSIONS_PER_PAGE > page) {
                        loadVersions(1, true);
                    }
                });

                pagination.find('.prev').click(function () {
                    if (page > 1) {
                        loadVersions(-1, true)
                    }
                });

                pagination.find('.page').click(function () {
                    var toPage = Number.parseInt($(this).text());

                    if(!isNaN(toPage)) {
                        loadVersions(toPage - page, true);
                    }
                });
            }

            // Sets tooltips up
            $('.version-list [data-toggle="tooltip"]').tooltip({
                container: 'body'
            });

            $(".loading").hide();
            versionList.show();
            $(".panel-pagination").show();

            if(scrollTop === true) {
                $("html, body").animate({ scrollTop: $('.version-list').offset().top - 130 }, 250);
            }
        }
    });
}

/*
 * ==================================================
 * =                   Doc ready                    =
 * ==================================================
 */

$(function () {
    loadVersions(1, false);

    // Setup channel list

    $('.list-channel').find('li').find('input').on('change', function () {
        var channelString = CHANNEL_STRING;
        var channelName = $(this).closest('.list-group-item').find('.channel').text();
        if ($(this).is(":checked")) {
            if (channelString.length) channelString += ',';
            channelString += channelName;
        } else {
            channelString = '';
            var checked = $('.list-channel').find('li').find('input:checked');
            checked.each(function (i) {
                channelString += $(this).closest('.list-group-item').find('.channel').text();
                if (i < checked.length - 1) channelString += ',';
            });
        }

        var url = '/' + PROJECT_OWNER + '/' + PROJECT_SLUG + '/versions';
        if (channelString.length) url += '?channels=' + channelString;

        go(url);
    });

    $('.channels-all').on('change', function () {
        var channelString = '';
        if (!$(this).is(":checked")) {
            var checked = $('.list-channel').find('li').find('input');
            checked.each(function (i) {
                channelString += $(this).closest('.list-group-item').find('.channel').text();
                if (i < checked.length - 1) channelString += ',';
            });
        }

        var url = '/' + PROJECT_OWNER + '/' + PROJECT_SLUG + '/versions';
        if (channelString.length) url += '?channels=' + channelString;

        go(url);
    });
});
