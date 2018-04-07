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
var TEXT_NOT_APPROVED = "";
var TEXT_NOT_APPROVED_CHANNEL = "";

var page = 0;

/*
 * ==================================================
 * =                Helper functions                =
 * ==================================================
 */

function createPage(page) {
    var pageTemplate = $("<li>");
    pageTemplate.addClass("page");
    var link = $("<a>");
    link.text(page);
    pageTemplate.append(link);

    return pageTemplate;
}

function loadVersions(increment, scrollTop) {
    var versionList = $('.version-table');

    var offset = (page + increment - 1) * VERSIONS_PER_PAGE;
    var url = '/api/projects/' + PLUGIN_ID + '/versions?offset=' + offset;
    if (CHANNEL_STRING) url += '&channels=' + CHANNEL_STRING;

    $.ajax({
        url: url,
        dataType: 'json',
        success: function (versions) {
            var versionTable = $(".version-table tbody");
            versionTable.empty();

            versions.forEach(function (version) {
                var row = $("<tr>");

                // ==> Base Info (channel, name)
                var baseInfo = $("<td>");
                baseInfo.addClass("base-info");

                var nameElement = $("<div>");
                nameElement.addClass("name");
                var nameLink = $("<a>");
                nameLink.text(version.name);
                nameLink.attr("href", version.href);
                nameElement.append(nameLink);
                baseInfo.append(nameElement);

                var channel = version.channel;
                var channelElement = $("<span>");
                channelElement.addClass("channel");
                channelElement.text(channel.name);
                channelElement.css("background", channel.color);
                baseInfo.append(channelElement);

                row.append(baseInfo);

                // => Tags

                var tags = $("<td>");
                tags.addClass("version-tags");
                version.tags.forEach(function (tag) {
                    var hasData = (tag.data !== "" && tag.data !== "null" && tag.data != null);

                    var tagContainer = $("<div>");
                    tagContainer.addClass("tags");
                    if(hasData) {
                        tagContainer.addClass("has-addons");
                    }

                    var tagElement = $("<span>");
                    tagElement.addClass("tag");
                    tagElement.text(tag.name);
                    tagElement.css("background", tag.backgroundColor);
                    tagElement.css("border-color", tag.backgroundColor);
                    tagElement.css("color", tag.foregroundColor);
                    tagContainer.append(tagElement);

                    if(hasData) {
                        var tagDataElement = $("<span>");
                        tagDataElement.addClass("tag");
                        tagDataElement.text(tag.data);
                        tagContainer.append(tagDataElement);
                    }

                    tags.append(tagContainer);
                });

                row.append(tags);

                // => Information One (created, size)

                var infoOne = $("<td>");
                infoOne.addClass("information-one");

                var createdContainer = $("<div>");
                createdContainer.append("<i class='fa fa-calendar'></i>");

                var created = $("<span>");
                created.text(moment(version.createdAt).format("MMM D, YYYY"));
                createdContainer.append(created);

                infoOne.append(createdContainer);


                var sizeContainer = $("<div>");
                sizeContainer.append("<i class='fa fa-file-o'></i>");

                var size = $("<span>");
                size.text(filesize(version.fileSize));
                sizeContainer.append(size);

                infoOne.append(sizeContainer);
                row.append(infoOne);

                // => Information Two (author, download count)

                var infoTwo = $("<td>");
                infoTwo.addClass("information-two");

                if(version.author != null) {
                    var authorContainer = $("<div>");
                    authorContainer.addClass("author");
                    authorContainer.append("<i class='fa fa-key'></i>");

                    var author = $("<span>");
                    author.text(version.author);
                    author.attr("title", "This version is signed by " + version.author);
                    author.attr("data-toggle", "tooltip");
                    author.attr("data-placement", "bottom");

                    authorContainer.append(author);
                    infoTwo.append(authorContainer);
                }

                var downloadContainer = $("<div>");
                downloadContainer.append("<i class='fa fa-download'></i>");
                var downloads = $("<span>");
                downloads.text(version.downloads + " Downloads");
                downloadContainer.append(downloads);
                infoTwo.append(downloadContainer);

                row.append(infoTwo);

                // => Download

                var download = $("<td>");
                download.addClass("download");

                var downloadLink = $("<a>");
                downloadLink.addClass("download-link");
                downloadLink.attr('href', version.href +  '/download/');

                downloadLink.append("<i class='fa fa-2x fa-download'></i>");

                if (!version.staffApproved) {
                    var text = channel.nonReviewed ? TEXT_NOT_APPROVED_CHANNEL : TEXT_NOT_APPROVED;

                    var warning = $("<i>");
                    warning.attr("title", text);
                    warning.attr("data-toggle", "tooltip");
                    warning.attr("data-placement", "bottom");
                    warning.addClass("fa fa-exclamation-circle");

                    downloadLink.append(warning);
                }

                download.append(downloadLink);
                row.append(download);

                versionTable.append(row);
            });

            // Sets the new page number
            page += increment;
            var totalPages = Math.ceil(TOTAL_VERSIONS / VERSIONS_PER_PAGE);

            if(totalPages > 1) {

                // Sets up the pagination
                var pagination = $(".version-panel .pagination");
                pagination.empty();

                var prev = $("<li>");
                prev.addClass("prev");
                if(page === 1) {
                    prev.addClass("disabled");
                }
                prev.append("<a>&laquo;</a>");
                pagination.append(prev);

                var left = totalPages - page;

                // Dot Template
                var dotTemplate = $("<li>");
                dotTemplate.addClass("disabled");
                var dotLink = $("<a>");
                dotLink.text("...");
                dotTemplate.append(dotLink);

                // [First] ...
                if(totalPages > 3 && page >= 3) {
                    pagination.append(createPage(1));

                    if(page > 3) {
                        pagination.append(dotTemplate);
                    }
                }

                //=> [current - 1] [current] [current + 1] logic
                if(totalPages > 2) {
                    if(left === 0) {
                        pagination.append(createPage((totalPages - 2)))
                    }
                }

                if(page !== 1) {
                    pagination.append(createPage((page -1)))
                }

                var activePage = $("<li>");
                activePage.addClass("page active");
                var link = $("<a>");
                link.text(page);
                activePage.append(link);
                pagination.append(activePage);


                if((page + 1) <= totalPages) {
                    pagination.append(createPage(page + 1))
                }

                if(totalPages > 2) {
                    if(page === 1) {
                        pagination.append(createPage(page + 2)) // Adds a third page if current page is first page
                    }
                }

                // [Last] ...
                if(totalPages > 3 && left > 1) {
                    if(left > 2) {
                        pagination.append(dotTemplate.clone());
                    }

                    pagination.append(createPage(totalPages));
                }

                // Builds the pagination

                var next = $("<li>");
                next.addClass("next");
                if(TOTAL_VERSIONS / VERSIONS_PER_PAGE <= page) {
                    next.addClass("disabled");
                }
                next.append("<a>&raquo;</a>");

                pagination.append(next);

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
                $("html, body").animate({ scrollTop: $('.version-table').offset().top - 130 }, 250);
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
