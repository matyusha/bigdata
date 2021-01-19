import java.sql.Timestamp

case class Row(
                Id:Integer,
                PostTypeId: Integer,
                CreationDate: Timestamp,
                Score: Integer,
                ViewCount: Integer,
                Body: String,
                OwnerUserId: Integer,
                LastEditorUserId: Integer,
                LastEditorDisplayName: String,
                LastEditDate: Timestamp,
                LastActivityDate: Timestamp,
                Title: String,
                Tags: String,
                AnswerCount: Integer,
                CommentCount: Integer,
                FavoriteCount: Integer,
                CommunityOwnedDate: Timestamp
              )
